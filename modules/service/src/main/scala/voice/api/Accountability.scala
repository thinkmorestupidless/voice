package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.{Principal, RequestContext}
import voice.agents.{
  ResearchSuggestions,
  SuggestedInvestigation,
  SuggestedQuestion,
  SuggestedSource
}
import voice.application.accountability.*
import voice.application.disclosure.ContributionRows
import voice.application.participation.StaffAccountEntity
import voice.application.issues.*
import voice.domain.*
import voice.domain.accountability.*
import voice.domain.issues.IssueState

import java.time.{Instant, LocalDate, ZoneOffset}

/** The bodies of the representative's routes (contracts/http-api.md, "Representative"). */
object AccountabilityJson:
  final case class QueueItem(
      kind: String,
      id: String,
      issue: Option[String],
      title: String,
      state: String,
      since: Instant,
      ageSeconds: Long,
      link: String
  )
  final case class ResearchRequest(question: String)
  final case class RunRef(run: String)
  final case class Labelled[A](label: String, item: A)
  final case class ResearchRun(
      run: String,
      issue: String,
      state: String,
      sources: Vector[Labelled[SuggestedSource]],
      questions: Vector[Labelled[SuggestedQuestion]],
      investigations: Vector[Labelled[SuggestedInvestigation]],
      recorded: Vector[String]
  )
  final case class InvestigationRequest(
      question: String = "",
      fromSuggestion: Option[String] = None
  )
  final case class TaskRequest(
      text: String,
      owner: String,
      due: LocalDate,
      state: Option[String] = None
  )
  final case class TaskView(id: String, text: String, owner: String, due: LocalDate, state: String)
  final case class InvestigationView(
      id: String,
      issue: String,
      question: String,
      state: String,
      reason: Option[String],
      records: Vector[String],
      tasks: Vector[TaskView]
  )
  final case class ActionRequest(
      authority: String = "",
      owner: String = "",
      date: Option[LocalDate] = None,
      rationale: String = "",
      state: String = "",
      publicSummary: String = "",
      investigation: Option[String] = None
  )
  final case class StateRequest(state: String)
  final case class DecisionRequest(
      approach: String = "",
      authority: String = "",
      alternatives: Vector[String] = Vector.empty,
      justification: String = ""
  )
  final case class UpdateRequest(text: String = "", corrects: Option[String] = None)
  final case class DevelopmentView(
      id: String,
      kind: String,
      ref: String,
      materiality: String,
      at: Instant
  )
  final case class OutcomeRequest(
      measured: String = "",
      limitations: String = "",
      claimsResolved: Boolean = false
  )

  given JsonValueCodec[QueueItem]                = Problems.codec[QueueItem]
  given queue: JsonValueCodec[Vector[QueueItem]] = Problems.codec[Vector[QueueItem]]
  given JsonValueCodec[ResearchRequest]          = Problems.codec[ResearchRequest]
  given JsonValueCodec[RunRef]                   = Problems.codec[RunRef]
  given JsonValueCodec[ResearchRun]              = Problems.codec[ResearchRun]
  given JsonValueCodec[InvestigationRequest]     = Problems.codec[InvestigationRequest]
  given JsonValueCodec[TaskRequest]              = Problems.codec[TaskRequest]
  given JsonValueCodec[InvestigationView]        = Problems.codec[InvestigationView]
  given JsonValueCodec[ActionRequest]            = Problems.codec[ActionRequest]
  given JsonValueCodec[StateRequest]             = Problems.codec[StateRequest]
  given JsonValueCodec[DecisionRequest]          = Problems.codec[DecisionRequest]
  given JsonValueCodec[OutcomeRequest]           = Problems.codec[OutcomeRequest]
  given JsonValueCodec[UpdateRequest]            = Problems.codec[UpdateRequest]
  given JsonValueCodec[DevelopmentView]          = Problems.codec[DevelopmentView]
  given JsonValueCodec[PublicJson.ActionView]    = Problems.codec[PublicJson.ActionView]
  given JsonValueCodec[PublicJson.DecisionView]  = Problems.codec[PublicJson.DecisionView]
  given JsonValueCodec[PublicJson.OutcomeView]   = Problems.codec[PublicJson.OutcomeView]

  val Suggested = "suggested"

  def investigation(s: InvestigationRecord): InvestigationView =
    InvestigationView(
      s.investigationId,
      s.issueId,
      s.question,
      s.status.label,
      s.suggestion.map(_.reason),
      s.suggestion.map(_.records).getOrElse(Vector.empty),
      s.tasks.map(t => TaskView(t.taskId, t.text, t.owner, t.due, t.state.label))
    )

import AccountabilityJson.{*, given}

/** The public records of action, as anyone reads them (FR-040 to FR-043). */
final class PublicAccountability(reach: Reach):

  private def entity(id: String) = reach.components.forEventSourcedEntity(EntityId(id))

  /**
   * The name a representative's records are signed with: the account's display name, never the
   * token's subject.
   */
  def signedBy(subject: String): String =
    Option(
      reach.components.forKeyValueEntity(EntityId(subject)).call(StaffAccountEntity.get).invoke()
    )
      .filter(_.provenance.isDefined)
      .map(_.displayName)
      .filter(_.nonEmpty)
      .getOrElse("a representative")

  def action(a: ActionRecord): PublicJson.ActionView =
    PublicJson.ActionView(
      a.actionId,
      a.stage.label,
      a.authority,
      a.owner,
      a.date,
      a.publicSummary,
      a.rationale,
      signedBy(a.recordedBy),
      a.recordedAt,
      completed = a.stage == ActionState.Completed,
      investigation = a.investigationId
    )

  def actions(s: IssueState): Vector[PublicJson.ActionView] =
    s.actions.map(id => entity(id).call(ActionEntity.get).invoke()).filter(_.exists).map(action)

  def investigations(s: IssueState): Vector[PublicJson.InvestigationSummary] =
    s.investigations
      .map(id => entity(id).call(InvestigationEntity.get).invoke())
      .filter(i => i.exists && i.status != InvestigationState.Suggested)
      .map(i =>
        PublicJson.InvestigationSummary(i.investigationId, i.question, i.status.label, i.openedAt)
      )

  def decision(d: DecisionRecord): PublicJson.DecisionView =
    PublicJson.DecisionView(
      d.decisionId,
      d.approach,
      d.authority,
      d.alternatives,
      d.justification,
      signedBy(d.recordedBy),
      d.recordedAt
    )

  def decisions(s: IssueState): Vector[PublicJson.DecisionView] =
    s.decisions
      .map(id => entity(id).call(DecisionEntity.get).invoke())
      .filter(_.exists)
      .map(decision)

  def outcome(o: OutcomeRecord): PublicJson.OutcomeView =
    PublicJson.OutcomeView(
      o.outcomeId,
      o.actionId,
      s"/public/issues/${o.issueId}#${o.actionId}",
      o.measured,
      o.limitations,
      o.claimsResolved,
      if o.claimsResolved then "The record says the problem this action addressed is resolved."
      else "The record does not say the problem is solved.",
      signedBy(o.recordedBy),
      o.recordedAt
    )

  def outcomes(s: IssueState): Vector[PublicJson.OutcomeView] =
    s.outcomes.map(id => entity(id).call(OutcomeEntity.get).invoke()).filter(_.exists).map(outcome)

  def updates(s: IssueState): Vector[PublicJson.UpdateView] =
    s.updates.map(u =>
      PublicJson.UpdateView(u.developmentId, u.text, signedBy(u.by), u.at, u.corrects)
    )

/**
 * The representative's work (FR-036 to FR-043). Every route here is the representative's alone, and
 * a refusal is logged (SC-006). A record is written first and linked to the Issue second; both are
 * idempotent on the record's id.
 */
final class StaffAccountability(reach: Reach, gate: StaffGate):

  private val public                  = PublicAccountability(reach)
  private def entity(id: String)      = reach.components.forEventSourcedEntity(EntityId(id))
  private def issueEntity(id: String) = entity(id)

  private def provenance(request: RequestContext, principal: Principal) =
    Requests.provenance(request, Requests.staff(principal), reach.now())

  private def held(issue: String): IssueState =
    val s =
      if Ids.is(IdKind.Issue, issue) then issueEntity(issue).call(IssueEntity.get).invoke()
      else IssueState.empty
    if !s.exists then throw Rules.IssueNotHeld.refused("There is no such issue.", "issue" -> issue)
    s

  private def age(since: Instant) =
    java.time.Duration.between(since, reach.now()).getSeconds.max(0L)

  /**
   * What waits, and for how long: new Issues, reviews, open prompts, investigations and follow-ups.
   */
  def queue(): Vector[QueueItem] =
    val investigations =
      reach.views.forView(InvestigationRows).ask(InvestigationRows.notClosed).toVector
    val touched = investigations.map(_.issueId).toSet
    val issues  = reach.views.forView(IssueRows).all().toVector
    val fresh = issues.filter(r => !touched(r.issueId)).map { r =>
      val since = r.openedAt.getOrElse(reach.now())
      QueueItem(
        "new issue",
        r.issueId,
        Some(r.issueId),
        r.title,
        "new, no investigation",
        since,
        age(since),
        s"/staff/issues/${r.issueId}"
      )
    }
    val reviews = reach.views
      .forView(ContributionRows)
      .ask(ContributionRows.inState, "state" -> ContributionState.UnderModeration.toString)
      .toVector
      .map { r =>
        val since = r.submittedAt.getOrElse(reach.now())
        QueueItem(
          "pending review",
          r.contributionId,
          r.associatedIssue,
          "A contribution awaiting review",
          ContributionState.UnderModeration.label,
          since,
          age(since),
          "/staff/reviews"
        )
      }
    val prompts = issues.flatMap { r =>
      issueEntity(r.issueId)
        .call(IssueEntity.get)
        .invoke()
        .reviewPrompts
        .filter(_.resolved.isEmpty)
        .map { p =>
          QueueItem(
            "review prompt",
            p.promptId,
            Some(r.issueId),
            s"Evidence was ${p.cause}; ${p.claims.size} claims to look at",
            "open",
            p.raisedAt,
            age(p.raisedAt),
            s"/staff/issues/${r.issueId}/evidence"
          )
        }
    }
    val open = investigations.map { i =>
      val label = InvestigationState.valueOf(i.status).label
      QueueItem(
        "investigation",
        i.investigationId,
        Some(i.issueId),
        i.question,
        label,
        i.createdAt,
        age(i.createdAt),
        s"/staff/investigations/${i.investigationId}"
      )
    }
    val followUps = investigations.flatMap { i =>
      i.awaitingExternal.map { t =>
        val since = i.openedAt.getOrElse(i.createdAt)
        QueueItem(
          "external follow-up",
          s"${i.investigationId}/$t",
          Some(i.issueId),
          i.question,
          TaskState.AwaitingExternal.label,
          since,
          age(since),
          s"/staff/investigations/${i.investigationId}"
        )
      }
    }
    (fresh ++ reviews ++ prompts ++ open ++ followUps).sortBy(-_.ageSeconds)

  def research(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: ResearchRequest
  ): RunRef =
    held(issue): Unit
    if body.question.trim.isEmpty then
      throw Action.checkFields("", "question" -> "").map(Refused(_)).get
    val p   = provenance(request, principal)
    val run = Ids.mint(IdKind.Investigation, p.at)
    reach.components
      .forWorkflow(EntityId(run))
      .call(ResearchWorkflow.start)
      .invoke(
        StartResearch(
          issue,
          body.question.trim,
          principal.subject,
          reach.edge.settings.reasoningService,
          p
        )
      ): Unit
    RunRef(run)

  def run(id: String): ResearchRun =
    val s = reach.components.forWorkflow(EntityId(id)).call(ResearchWorkflow.status).invoke()
    if s.status == ResearchStatus.NotStarted then
      throw Rules.IssueNotHeld.refused("There is no such research run.", "issue" -> "")
    val got = s.suggestions.getOrElse(ResearchSuggestions())
    ResearchRun(
      id,
      s.issueId,
      s.status.toString.toLowerCase,
      got.sources.map(Labelled(Suggested, _)),
      got.questions.map(Labelled(Suggested, _)),
      got.investigations.map(Labelled(Suggested, _)),
      s.investigations
    )

  def investigation(id: String): InvestigationView =
    val s = entity(id).call(InvestigationEntity.get).invoke()
    if !s.exists then
      throw Rules.IssueNotHeld.refused("There is no such investigation.", "issue" -> "")
    AccountabilityJson.investigation(s)

  /** Opening a suggestion is approving it; otherwise a new investigation is opened. */
  def openInvestigation(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: InvestigationRequest
  ): InvestigationView =
    held(issue): Unit
    val p = provenance(request, principal)
    val id = body.fromSuggestion.filter(_.nonEmpty) match
      case Some(s) =>
        val held = entity(s).call(InvestigationEntity.get).invoke()
        if !held.exists || held.issueId != issue then
          throw Rules.IssueNotHeld.refused(
            "There is no such suggestion on this issue.",
            "issue" -> issue
          )
        s
      case None => Ids.mint(IdKind.Investigation, p.at)
    val opened = entity(id)
      .call(InvestigationEntity.open)
      .invoke(OpenInvestigation(issue, body.question, principal.subject, p))
    issueEntity(issue)
      .call(IssueEntity.linkInvestigation)
      .invoke(LinkInvestigation(id, opened.question, p)): Unit
    AccountabilityJson.investigation(opened)

  def addTask(
      request: RequestContext,
      principal: Principal,
      investigation: String,
      body: TaskRequest
  ): InvestigationView =
    val p     = provenance(request, principal)
    val held  = entity(investigation).call(InvestigationEntity.get).invoke()
    val state = body.state.flatMap(TaskState.parse).getOrElse(TaskState.Open)
    val task =
      Task(s"task-${held.tasks.size + 1}", body.text.trim, body.owner.trim, body.due, state, p.at)
    AccountabilityJson.investigation(
      entity(investigation).call(InvestigationEntity.addTask).invoke(AddTask(task, p))
    )

  /**
   * An action with every field a reader is owed, dated today or earlier and not before the Issue.
   */
  def recordAction(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: ActionRequest
  ): PublicJson.ActionView =
    val s     = held(issue)
    val p     = provenance(request, principal)
    val id    = Ids.mint(IdKind.Action, p.at)
    val stage = ActionState.parse(body.state.trim)
    if stage.isEmpty && body.state.trim.nonEmpty then
      throw Refused(Action.unknownState(id, body.state.trim))
    val missing = Seq("date" -> body.date.fold("")(_.toString), "state" -> stage.fold("")(_.wire))
    Action.checkFields(id, missing*).foreach(r => throw Refused(r))
    body.investigation.filter(_.nonEmpty).foreach { inv =>
      Investigation
        .checkOpen(entity(inv).call(InvestigationEntity.get).invoke(), inv)
        .foreach(r => throw Refused(r))
    }
    val record = ActionRecord(
      id,
      issue,
      body.investigation.filter(_.nonEmpty),
      body.authority.trim,
      body.owner.trim,
      body.date.get,
      body.rationale.trim,
      body.publicSummary.trim,
      stage.get,
      principal.subject,
      p.at,
      Ids.mint(IdKind.Development, p.at)
    )
    val today  = p.at.atZone(ZoneOffset.UTC).toLocalDate
    val opened = s.openedAt.getOrElse(p.at).atZone(ZoneOffset.UTC).toLocalDate
    val saved  = entity(id).call(ActionEntity.record).invoke(RecordAction(record, today, opened, p))
    issueEntity(issue)
      .call(IssueEntity.linkAction)
      .invoke(LinkAction(id, saved.publicSummary, saved.developmentId, p)): Unit
    public.action(saved)

  def changeState(
      request: RequestContext,
      principal: Principal,
      action: String,
      body: StateRequest
  ): PublicJson.ActionView =
    val stage = ActionState
      .parse(body.state.trim)
      .getOrElse(
        throw Refused(
          if body.state.trim.isEmpty then Action.checkFields(action, "state" -> "").get
          else Action.unknownState(action, body.state.trim)
        )
      )
    public.action(
      entity(action)
        .call(ActionEntity.changeStage)
        .invoke(ChangeStage(stage, provenance(request, principal)))
    )

  def recordDecision(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: DecisionRequest
  ): PublicJson.DecisionView =
    held(issue): Unit
    val p  = provenance(request, principal)
    val id = Ids.mint(IdKind.Decision, p.at)
    val record = DecisionRecord(
      id,
      issue,
      body.approach.trim,
      body.authority.trim,
      body.alternatives.map(_.trim).filter(_.nonEmpty),
      body.justification.trim,
      principal.subject,
      p.at,
      Ids.mint(IdKind.Development, p.at)
    )
    val saved = entity(id).call(DecisionEntity.record).invoke(RecordDecision(record, p))
    issueEntity(issue)
      .call(IssueEntity.linkDecision)
      .invoke(LinkDecision(id, saved.approach, saved.developmentId, p)): Unit
    public.decision(saved)

  /**
   * A narrative update, written or reviewed by the representative (FR-044): a development like any
   * other, so its followers are told. A correction of an earlier update's wording is minor, and
   * tells no one.
   */
  def publishUpdate(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: UpdateRequest
  ): DevelopmentView =
    held(issue): Unit
    val p  = provenance(request, principal)
    val id = Ids.mint(IdKind.Development, p.at)
    val after = issueEntity(issue)
      .call(IssueEntity.publishUpdate)
      .invoke(PublishUpdate(id, body.text, principal.subject, body.corrects.filter(_.nonEmpty), p))
    val d = after
      .development(id)
      .getOrElse(
        throw Rules.UpdateTextRequired.refused("The update was not published.", "issue" -> issue)
      )
    DevelopmentView(d.developmentId, d.kind.wire, d.ref, d.materiality.toString.toLowerCase, d.at)

  /** An outcome names an action on its Issue; it is resolved only if the body says so. */
  def recordOutcome(
      request: RequestContext,
      principal: Principal,
      action: String,
      body: OutcomeRequest
  ): PublicJson.OutcomeView =
    val a = entity(action).call(ActionEntity.get).invoke()
    if !a.exists then
      throw Rules.OutcomeActionNotHeld.refused(
        "There is no such action.",
        "issue"  -> "",
        "action" -> action
      )
    val s  = held(a.issueId)
    val p  = provenance(request, principal)
    val id = Ids.mint(IdKind.Outcome, p.at)
    val record = OutcomeRecord(
      id,
      a.issueId,
      action,
      body.measured.trim,
      body.limitations.trim,
      body.claimsResolved,
      principal.subject,
      p.at,
      Ids.mint(IdKind.Development, p.at)
    )
    val saved = entity(id).call(OutcomeEntity.record).invoke(RecordOutcome(record, s.actions, p))
    issueEntity(a.issueId)
      .call(IssueEntity.linkOutcome)
      .invoke(LinkOutcome(id, action, saved.developmentId, p)): Unit
    public.outcome(saved)
