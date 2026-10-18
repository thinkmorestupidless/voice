package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.*
import voice.application.disclosure.DisclosurePurposes
import voice.application.issues.*
import voice.domain.*
import voice.domain.issues.*

import java.time.Instant

/**
 * The public bodies (contracts/http-api.md, "Public"). None holds a sealed value or a participant
 * id; an attributed copy's text and name are opened here, at the edge, and an erased author's open
 * as unavailable (R2).
 */
object PublicJson:
  final case class CountsView(
      contributions: Int,
      withdrawn: Int,
      authorsErased: Int,
      erasedOnRequest: Int,
      erasedForDormancy: Int,
      caveat: String
  )
  final case class IssueSummary(
      id: String,
      title: String,
      summary: String,
      counts: CountsView,
      lastDevelopmentAt: Option[Instant]
  )
  final case class PublicContributionView(
      pub: String,
      text: String,
      attribution: Option[String],
      publishedAt: Instant,
      authorErased: Option[String]
  )

  /** One chronology entry; a submission no representative has answered says so (FR-041, FR-042). */
  final case class EntryView(
      state: String,
      note: String,
      actor: String,
      at: Instant,
      ref: Option[String],
      unanswered: Boolean = false
  )
  final case class IssuePage(
      id: String,
      title: String,
      summary: String,
      contributions: Vector[PublicContributionView],
      counts: CountsView,
      chronology: Vector[EntryView],
      limitations: String,
      questions: Vector[voice.application.reasoning.QuestionAnswer] = Vector.empty,
      evidence: Vector[voice.application.reasoning.EvidenceCard] = Vector.empty,
      claims: Vector[voice.application.reasoning.ClaimSummary] = Vector.empty,
      actions: Vector[ActionView] = Vector.empty,
      investigations: Vector[InvestigationSummary] = Vector.empty,
      decisions: Vector[DecisionView] = Vector.empty,
      outcomes: Vector[OutcomeView] = Vector.empty,
      updates: Vector[UpdateView] = Vector.empty,
      options: Vector[DeliberationJson.OptionView] = Vector.empty,
      aggregate: Option[voice.domain.deliberation.Aggregate] = None
  )

  /** An accountable action as the public sees it (FR-040, FR-041): `completed` only when it is. */
  final case class ActionView(
      id: String,
      state: String,
      authority: String,
      owner: String,
      date: java.time.LocalDate,
      summary: String,
      rationale: String,
      recordedBy: String,
      recordedAt: Instant,
      completed: Boolean,
      investigation: Option[String] = None
  )

  /**
   * A representative's narrative update as published (FR-044); a correction names what it corrects.
   */
  final case class UpdateView(
      id: String,
      text: String,
      publishedBy: String,
      publishedAt: Instant,
      corrects: Option[String]
  )
  final case class InvestigationSummary(
      id: String,
      question: String,
      state: String,
      openedAt: Option[Instant]
  )
  final case class DecisionView(
      id: String,
      approach: String,
      authority: String,
      alternatives: Vector[String],
      justification: String,
      recordedBy: String,
      recordedAt: Instant
  )
  final case class OutcomeView(
      id: String,
      action: String,
      actionLink: String,
      measured: String,
      limitations: String,
      claimsResolved: Boolean,
      resolution: String,
      recordedBy: String,
      recordedAt: Instant
  )

  given issueSummaries: JsonValueCodec[Vector[IssueSummary]] = Problems.codec[Vector[IssueSummary]]
  given JsonValueCodec[IssuePage]                            = Problems.codec[IssuePage]
  given entries: JsonValueCodec[Vector[EntryView]]           = Problems.codec[Vector[EntryView]]

  def counts(c: IssueCounts): CountsView =
    CountsView(
      c.contributions,
      c.withdrawn,
      c.erasedRequested + c.erasedDormancy,
      c.erasedRequested,
      c.erasedDormancy,
      IssueCounts.Caveat
    )

  val Limitations: String =
    "This issue holds what residents chose to share after a reviewer checked it, and what Voice's staff recorded. It is not a survey, it does not say how many people agree, and an absent record means nothing was recorded, not that nothing happened."

import PublicJson.{*, given}

/** The public reading of Issues, opened at the edge. */
final class PublicIssues(reach: Reach):

  val evidence: PublicEvidence             = PublicEvidence(reach)
  val accountability: PublicAccountability = PublicAccountability(reach)

  given JsonValueCodec[String] = Problems.codec[String]

  def list(q: Option[String], limit: Int): Vector[IssueSummary] =
    val words = q.map(IssueSuggestions.words).getOrElse(Set.empty)
    reach.views
      .forView(IssueRows)
      .all()
      .toVector
      .filter(r =>
        words.isEmpty || (IssueSuggestions.words(r.title) ++ IssueSuggestions
          .words(r.summary)).intersect(words).nonEmpty
      )
      .sortBy(r => -r.lastDevelopmentAt.map(_.toEpochMilli).getOrElse(0L))
      .take(limit.max(1).min(100))
      .map(r => IssueSummary(r.issueId, r.title, r.summary, counts(r.counts), r.lastDevelopmentAt))

  /**
   * A private, unknown or never-opened Issue answers as if it never existed (`public.not-found`).
   */
  def held(id: String): IssueState =
    if !Ids.is(IdKind.Issue, id) then throw Rules.PublicNotFound.refused("There is no such record.")
    val state = reach.components.forEventSourcedEntity(EntityId(id)).call(IssueEntity.get).invoke()
    if !state.exists then throw Rules.PublicNotFound.refused("There is no such record.")
    state

  def page(id: String): IssuePage =
    val s = held(id)
    val deidentified = s.contributions.map(c =>
      PublicContributionView(c.pub, c.text, None, c.publishedAt, c.authorErased.map(_.wire))
    )
    val attributed = s.attributed.map { c =>
      PublicContributionView(
        c.pub,
        reach.sealing.openText(c.text, DisclosurePurposes.IssueText),
        Some(reach.sealing.openText(c.name, DisclosurePurposes.IssueName)),
        c.publishedAt,
        None
      )
    }
    IssuePage(
      id,
      s.title,
      s.summary,
      (deidentified ++ attributed).sortBy(_.publishedAt),
      counts(s.counts),
      chronology(s),
      Limitations,
      questions = evidence.questions(id, s),
      evidence = evidence.cards(id, s),
      claims = evidence.claims(id, s),
      actions = accountability.actions(s),
      investigations = accountability.investigations(s),
      decisions = accountability.decisions(s),
      outcomes = accountability.outcomes(s),
      updates = accountability.updates(s),
      options = Deliberation.options(reach, s),
      aggregate = Some(Deliberation.aggregate(reach, id, s))
    )

  def chronology(s: IssueState): Vector[EntryView] =
    s.chronology.zipWithIndex.map { (e, i) =>
      val answered = s.chronology
        .drop(i + 1)
        .exists(later => later.actorKind == "staff" && later.state != ChronologyState.Submitted)
      EntryView(
        e.state.label,
        e.note,
        e.actorKind,
        e.at,
        Option(e.ref).filter(_.nonEmpty),
        unanswered = e.state == ChronologyState.Submitted && !answered
      )
    }

/**
 * Anyone, with no credential (FR-019, FR-026). No route here creates anything, and no response type
 * holds a sealed value or a participant id: a test over the response types holds it to that.
 */
final class PublicEndpoint(reach: Reach) extends HttpEndpoint("/public"):
  val acl: Acl = Acl.AllowAll

  private val issues = PublicIssues(reach)

  get("/issues") { () =>
    Problems.handle {
      Problems.ok(
        issues.list(
          request.query.optional[String]("q"),
          request.query.optional[Int]("limit").getOrElse(50)
        )
      )
    }
  }

  get("/issues/{issue}") { (issue: String) =>
    Problems.handle(Problems.ok(issues.page(issue)))
  }

  get("/issues/{issue}/chronology") { (issue: String) =>
    Problems.handle(Problems.ok(issues.chronology(issues.held(issue))))
  }

  // ── evidence, claims, questions, sources and search (US3) ─────────────────

  import EvidenceJson.given

  get("/issues/{issue}/evidence/{evidence}") { (issue: String, link: String) =>
    Problems.handle(Problems.ok(issues.evidence.evidence(issue, issues.held(issue), link)))
  }

  get("/issues/{issue}/claims/{claim}") { (issue: String, claim: String) =>
    Problems.handle(Problems.ok(issues.evidence.claim(issue, issues.held(issue), claim)))
  }

  get("/issues/{issue}/questions/{question}") { (issue: String, question: String) =>
    Problems.handle(Problems.ok(issues.evidence.question(issue, issues.held(issue), question)))
  }

  get("/sources/{source}") { (source: String) =>
    Problems.handle(Problems.ok(issues.evidence.source(source)))
  }

  /** No session, nothing created: a reader searching is not a follower (FR-045). */
  get("/search") { () =>
    Problems.handle {
      Problems.ok(
        issues.evidence.search(
          request.query.optional[String]("q").getOrElse(""),
          request.query.optional[Int]("limit").getOrElse(20)
        )
      )
    }
  }
