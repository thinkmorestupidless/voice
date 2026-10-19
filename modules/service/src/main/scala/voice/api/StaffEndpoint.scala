package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.*
import voice.application.accountability.*
import voice.application.participation.*
import voice.domain.*
import voice.identity.{StaffAcl, StaffRole}

object StaffJson:
  final case class AccountRequest(
      displayName: String,
      authority: Option[String] = None,
      active: Boolean = true
  )
  final case class Account(
      principal: String,
      displayName: String,
      authority: Option[String],
      active: Boolean
  )
  given JsonValueCodec[AccountRequest]                = Problems.codec[AccountRequest]
  given JsonValueCodec[Account]                       = Problems.codec[Account]
  given accounts: JsonValueCodec[Vector[Account]]     = Problems.codec[Vector[Account]]
  given accessRows: JsonValueCodec[Vector[AccessRow]] = Problems.codec[Vector[AccessRow]]

  def account(s: StaffAccountState): Account =
    Account(s.principal, s.displayName, s.authority, s.active)

import StaffJson.{*, given}
import DisclosureJson.{*, given}

/**
 * What every staff route does first: the role the route needs, from the token (R10), and that the
 * account has not been deprovisioned (`staff.inactive`). A read of a restricted record is appended
 * to the access log after the role check (FR-005).
 */
final class StaffGate(reach: Reach):

  def account(subject: String): Option[StaffAccountState] =
    val held =
      reach.components.forKeyValueEntity(EntityId(subject)).call(StaffAccountEntity.get).invoke()
    Option.when(held.provenance.isDefined)(held)

  def admit(principal: Principal, rule: Rule, roles: StaffRole*): Unit =
    StaffAcl.require(principal, rule, roles*)
    account(principal.subject).filterNot(_.active).foreach { _ =>
      throw Rules.StaffInactive.refused(
        "This staff account has been deprovisioned.",
        "principal" -> principal.subject
      )
    }

  /** As `admit`, and a refusal is written to the access log before it is answered (SC-006). */
  def admitLogged(
      request: RequestContext,
      principal: Principal,
      rule: Rule,
      roles: StaffRole*
  ): Unit =
    try admit(principal, rule, roles*)
    catch
      case refused: Refused =>
        logAccess(
          request,
          principal,
          s"refused ${request.method} ${request.path}",
          refused.refusal.rule
        )
        throw refused

  def logAccess(
      request: RequestContext,
      principal: Principal,
      route: String,
      recordId: String
  ): Unit =
    val at = reach.now()
    reach.components
      .forEventSourcedEntity(EntityId(AccessLogEntity.dayOf(at)))
      .call(AccessLogEntity.record)
      .invoke(
        RecordAccess(
          AccessEntry(principal.subject, route, recordId, at),
          Requests.provenance(request, Requests.staff(principal), at)
        )
      ): Unit

/** Representatives, reviewers and operators, by a token from the staff issuer (R10). */
final class StaffEndpoint(reach: Reach) extends HttpEndpoint("/staff"):
  val acl: Acl = reach.edge.staffAcl

  private val gate    = StaffGate(reach)
  private val reviews = Reviews(reach, gate)
  private val rights  = DataRights(reach)

  // ── operator ───────────────────────────────────────────────────────────────

  putBody("/accounts/{principal}") { (subject: String, body: AccountRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.StaffNotOperator, StaffRole.Operator)
      val at = reach.now()
      val state = reach.components
        .forKeyValueEntity(EntityId(subject))
        .call(StaffAccountEntity.write)
        .invoke(
          WriteStaffAccount(
            body.displayName,
            body.authority,
            body.active,
            Requests.provenance(request, Requests.staff(principal), at)
          )
        )
      Problems.ok(account(state))
    }
  }

  // ── erasures (US7): operators only, receipts with no personal value ──────

  get("/erasures") { () =>
    import DataRightsJson.given
    Problems.handle {
      gate.admitLogged(request, principal, Rules.ErasureNotOperator, StaffRole.Operator)
      Problems.ok(rights.rows(request.query.optional[String]("state")))
    }
  }

  postBody("/erasures/{participant}/verify") {
    (participant: String, _: DataRightsJson.VerifyBody) =>
      import DataRightsJson.given
      Problems.handle {
        gate.admitLogged(request, principal, Rules.ErasureNotOperator, StaffRole.Operator)
        Problems.ok(rights.verify(principal, participant))
      }
  }

  get("/accounts") { () =>
    Problems.handle {
      gate.admit(principal, Rules.StaffNotOperator, StaffRole.Operator)
      Problems.ok(
        reach.views.forView(StaffAccountRows).all().map(account).toVector.sortBy(_.principal)
      )
    }
  }

  get("/access-log") { () =>
    Problems.handle {
      gate.admit(principal, Rules.StaffNotOperator, StaffRole.Operator)
      val since = request.query.optional[String]("since").getOrElse("")
      Problems.ok(
        reach.views.forView(AccessLogRows).ask(AccessLogRows.since, "since" -> since).toVector
      )
    }
  }

  // ── reviewer (US2) ─────────────────────────────────────────────────────────

  get("/reviews") { () =>
    Problems.handle {
      gate.admit(principal, Rules.ReviewNotReviewer, StaffRole.Reviewer)
      Problems.ok(
        reviews.queue(
          request,
          principal,
          request.query.optional[String]("state").getOrElse("UnderModeration")
        )
      )
    }
  }

  postBody("/reviews/{contribution}/enhanced") { (contribution: String, body: ReasonRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.ReviewNotReviewer, StaffRole.Reviewer)
      Problems.ok(reviews.enhanced(request, principal, contribution, body))
    }
  }

  postBody("/reviews/{contribution}/approve") { (contribution: String, body: ApproveRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.ReviewNotReviewer, StaffRole.Reviewer)
      Problems.ok(reviews.approve(request, principal, contribution, body))
    }
  }

  postBody("/reviews/{contribution}/reject") { (contribution: String, body: ReasonRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.ReviewNotReviewer, StaffRole.Reviewer)
      Problems.ok(reviews.reject(request, principal, contribution, body))
    }
  }

  // ── reviewer: issues, sources, questions, evidence and claims (US3) ────────

  private val evidence = StaffEvidenceRoutes(reach)

  import EvidenceJson.{*, given}

  postBody("/issues") { (body: NewIssueRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
      Problems.created(evidence.openIssue(request, principal, body))
    }
  }

  postBody("/sources") { (body: SourceRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
      Problems.ok(evidence.source(request, principal, body))
    }
  }

  postBody("/issues/{issue}/questions") { (issue: String, body: QuestionRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
      Problems.ok(evidence.question(request, principal, issue, body))
    }
  }

  get("/issues/{issue}/evidence") { (issue: String) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer, StaffRole.Representative)
      Problems.ok(evidence.evidence(issue))
    }
  }

  postBody("/issues/{issue}/evidence") { (issue: String, body: EvidenceRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
      Problems.created(evidence.propose(request, principal, issue, body))
    }
  }

  postBody("/issues/{issue}/evidence/{evidence}/accept") {
    (issue: String, link: String, _: EmptyBody) =>
      Problems.handle {
        gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
        Problems.ok(evidence.accept(request, principal, issue, link))
      }
  }

  postBody("/issues/{issue}/evidence/{evidence}/reject") {
    (issue: String, link: String, body: ReasonBody) =>
      Problems.handle {
        gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
        Problems.ok(evidence.reject(request, principal, issue, link, body))
      }
  }

  postBody("/issues/{issue}/evidence/{evidence}/supersede") {
    (issue: String, link: String, body: SupersedeBody) =>
      Problems.handle {
        gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
        Problems.ok(evidence.supersede(request, principal, issue, link, body))
      }
  }

  postBody("/issues/{issue}/claims") { (issue: String, body: ClaimRequest) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
      Problems.ok(evidence.claim(request, principal, issue, body))
    }
  }

  get("/issues/{issue}/review-prompts") { (issue: String) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer, StaffRole.Representative)
      Problems.ok(evidence.reviewPrompts(issue, request.query.optional[String]("state")))
    }
  }

  postBody("/issues/{issue}/review-prompts/{prompt}/resolve") {
    (issue: String, promptId: String, body: NoteBody) =>
      Problems.handle {
        gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer)
        Problems.ok(evidence.resolve(request, principal, issue, promptId, body))
      }
  }

  // ── options and the representative's view (US4) ───────────────────────────

  private val deliberation = StaffDeliberation(reach, gate)

  import DeliberationJson.{OptionBody, given}

  putBody("/issues/{issue}/options/{option}") { (issue: String, option: String, body: OptionBody) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer, StaffRole.Representative)
      Problems.ok(deliberation.writeOption(request, principal, issue, option, body))
    }
  }

  get("/issues/{issue}/options") { (issue: String) =>
    Problems.handle {
      gate.admit(principal, Rules.EvidenceNotReviewer, StaffRole.Reviewer, StaffRole.Representative)
      Problems.ok(deliberation.options(issue))
    }
  }

  get("/issues/{issue}") { (issue: String) =>
    Problems.handle {
      gate.admit(principal, Rules.InvestigationNotRepresentative, StaffRole.Representative)
      Problems.ok(deliberation.forStaff(request, principal, issue))
    }
  }

  // ── the representative: queue, research, investigations and actions (US5) ──

  private val accountability = StaffAccountability(reach, gate)

  import AccountabilityJson.{*, given}

  private def representative(): Unit =
    gate.admitLogged(
      request,
      principal,
      Rules.InvestigationNotRepresentative,
      StaffRole.Representative
    )

  get("/queue") { () =>
    Problems.handle {
      representative()
      Problems.ok(accountability.queue())
    }
  }

  postBody("/issues/{issue}/research") { (issue: String, body: ResearchRequest) =>
    Problems.handle {
      representative()
      Problems.accepted(accountability.research(request, principal, issue, body))
    }
  }

  get("/research/{run}") { (run: String) =>
    Problems.handle {
      representative()
      Problems.ok(accountability.run(run))
    }
  }

  postBody("/issues/{issue}/investigations") { (issue: String, body: InvestigationRequest) =>
    Problems.handle {
      representative()
      Problems.created(accountability.openInvestigation(request, principal, issue, body))
    }
  }

  get("/investigations/{investigation}") { (investigation: String) =>
    Problems.handle {
      representative()
      Problems.ok(accountability.investigation(investigation))
    }
  }

  postBody("/investigations/{investigation}/tasks") { (investigation: String, body: TaskRequest) =>
    Problems.handle {
      representative()
      Problems.ok(accountability.addTask(request, principal, investigation, body))
    }
  }

  postBody("/issues/{issue}/actions") { (issue: String, body: ActionRequest) =>
    Problems.handle {
      representative()
      Problems.created(accountability.recordAction(request, principal, issue, body))
    }
  }

  postBody("/actions/{action}/state") { (action: String, body: StateRequest) =>
    Problems.handle {
      representative()
      Problems.ok(accountability.changeState(request, principal, action, body))
    }
  }

  postBody("/issues/{issue}/decisions") { (issue: String, body: DecisionRequest) =>
    Problems.handle {
      representative()
      Problems.created(accountability.recordDecision(request, principal, issue, body))
    }
  }

  postBody("/actions/{action}/outcomes") { (action: String, body: OutcomeRequest) =>
    Problems.handle {
      representative()
      Problems.created(accountability.recordOutcome(request, principal, action, body))
    }
  }

  // ── narrative updates (US6) ───────────────────────────────────────────────

  postBody("/issues/{issue}/updates") { (issue: String, body: UpdateRequest) =>
    Problems.handle {
      representative()
      Problems.created(accountability.publishUpdate(request, principal, issue, body))
    }
  }
