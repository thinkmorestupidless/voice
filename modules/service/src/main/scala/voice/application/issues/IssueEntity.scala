package voice.application.issues

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.issues.*
import voice.domain.issues.IssueEvent.*

final case class OpenIssue(title: String, summary: String, provenance: Provenance)

/** What the review workflow writes to an Issue: one copy, of the kind the mode allows (R2). */
enum Copy:
  case Public(pub: String, text: String)
  case Attributed(pub: String, text: Sealed[String], name: Sealed[String])
  case Restricted(pub: String, text: Sealed[String])

  def ref: String = this match
    case Public(p, _)        => p
    case Attributed(p, _, _) => p
    case Restricted(p, _)    => p

final case class PublishCopy(copy: Copy, provenance: Provenance)
final case class WithdrawCopy(pub: String, provenance: Provenance)
final case class LinkQuestion(question: QuestionRef, provenance: Provenance)
final case class LinkClaim(claim: LinkedClaim, provenance: Provenance)
final case class ProposeEvidence(link: EvidenceLink, provenance: Provenance)
final case class AcceptEvidence(
    linkId: String,
    evidence: EvidenceRef,
    reviewer: String,
    promptId: String,
    provenance: Provenance,
    developmentId: String = ""
)
final case class RejectEvidence(
    linkId: String,
    reviewer: String,
    reason: String,
    provenance: Provenance
)
final case class SupersedeEvidence(
    linkId: String,
    by: String,
    reviewer: String,
    reason: String,
    promptId: String,
    provenance: Provenance
)
final case class ResolvePrompt(promptId: String, by: String, note: String, provenance: Provenance)
final case class AddOption(optionId: String, title: String, provenance: Provenance)
final case class LinkInvestigation(
    investigationId: String,
    question: String,
    provenance: Provenance
)
final case class LinkAction(
    actionId: String,
    summary: String,
    developmentId: String,
    provenance: Provenance
)
final case class LinkDecision(
    decisionId: String,
    approach: String,
    developmentId: String,
    provenance: Provenance
)
final case class LinkOutcome(
    outcomeId: String,
    actionId: String,
    developmentId: String,
    provenance: Provenance
)
final case class MarkAuthorErased(pub: String, cause: ErasureCause, provenance: Provenance)
final case class PublishUpdate(
    developmentId: String,
    text: String,
    by: String,
    corrects: Option[String],
    provenance: Provenance
)

/**
 * A shared public Issue (FR-020). It holds public copies under `pub-…` references and no
 * participant identifier (R3). A repeated publication or withdrawal of one reference answers the
 * Issue unchanged (`issue.contribution.duplicate` is an answer, not an error).
 */
final class IssueEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[IssueState, IssueEvent]:

  private def id = context.entityId

  def emptyState: IssueState = IssueState.empty

  def applyEvent(event: IssueEvent): IssueState = Issue(currentState, event)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def open(c: OpenIssue): Effect[IssueState] =
    if currentState.exists then effects.reply(currentState)
    else effects.persist(IssueOpened(c.title.trim, c.summary.trim, c.provenance)).thenReply(s => s)

  def publish(c: PublishCopy): Effect[IssueState] =
    Issue.checkHeld(currentState, id) match
      case Some(r)                                => refuse(r)
      case None if currentState.known(c.copy.ref) => effects.reply(currentState)
      case None =>
        val event = c.copy match
          case Copy.Public(pub, text) => ContributionPublished(pub, text, c.provenance)
          case Copy.Attributed(pub, text, name) =>
            ContributionAttributed(pub, text, name, c.provenance)
          case Copy.Restricted(pub, text) => ContributionRestricted(pub, text, c.provenance)
        effects.persist(event).thenReply(s => s)

  /**
   * A retained de-identified copy whose author was erased: marked once, counted by cause (FR-064).
   */
  def markAuthorErased(c: MarkAuthorErased): Effect[IssueState] =
    currentState.contributions.find(_.pub == c.pub) match
      case Some(copy) if copy.authorErased.isEmpty =>
        effects.persist(AuthorErased(c.pub, c.cause, c.provenance)).thenReply(s => s)
      case _ => effects.reply(currentState)

  def withdraw(c: WithdrawCopy): Effect[IssueState] =
    if !currentState.holds(c.pub) then effects.reply(currentState)
    else effects.persist(ContributionWithdrawn(c.pub, c.provenance)).thenReply(s => s)

  // ── questions, claims and evidence (US3) ───────────────────────────────────

  private def held[R](body: => Effect[R]): Effect[R] =
    Issue.checkHeld(currentState, id).fold(body)(refuse)

  /** A question already linked answers 200, unchanged (`issue.question.duplicate`). */
  def linkQuestion(c: LinkQuestion): Effect[IssueState] = held {
    if currentState.questions.contains(c.question) then effects.reply(currentState)
    else effects.persist(QuestionLinked(c.question, c.provenance)).thenReply(s => s)
  }

  /** A claim is linked only from evidence this Issue accepted (FR-021, R4). */
  def linkClaim(c: LinkClaim): Effect[IssueState] = held {
    if currentState.claims.exists(_.claim == c.claim.claim) then effects.reply(currentState)
    else
      c.claim.derivesFrom.find(ref =>
        !currentState.linkOf(ref).exists(_.state == EvidenceState.Accepted)
      ) match
        case Some(ref) =>
          refuse(
            Rules.EvidenceReferenceUnresolved.refusal(
              "A claim derives only from evidence accepted on this issue.",
              "kind" -> "evidence",
              "id"   -> ref.id
            )
          )
        case None if c.claim.derivesFrom.isEmpty =>
          refuse(
            Rules.EvidenceReferenceUnresolved.refusal(
              "A claim derives from at least one piece of evidence.",
              "kind" -> "evidence",
              "id"   -> ""
            )
          )
        case None => effects.persist(ClaimLinked(c.claim, c.provenance)).thenReply(s => s)
  }

  def proposeEvidence(c: ProposeEvidence): Effect[IssueState] = held {
    if currentState.link(c.link.linkId).isDefined then effects.reply(currentState)
    else
      effects
        .persist(EvidenceProposed(c.link.copy(state = EvidenceState.Candidate), c.provenance))
        .thenReply(s => s)
  }

  /** Acceptance is a civic decision; it prompts a review of what the evidence bears on (FR-028). */
  def acceptEvidence(c: AcceptEvidence): Effect[IssueState] = held {
    Issue.checkMove(currentState, id, c.linkId, EvidenceState.Accepted) match
      case Left(r)                                       => refuse(r)
      case Right(l) if l.state == EvidenceState.Accepted => effects.reply(currentState)
      case Right(l) =>
        val accepted = l.copy(evidence = Some(c.evidence))
        val claims   = currentState.claimsBearingOn(accepted)
        val prompt = Option.when(claims.nonEmpty)(
          ReviewPrompt(c.promptId, c.linkId, "accepted", claims, Vector.empty, c.provenance.at)
        )
        effects
          .persistAll(
            Seq(EvidenceAccepted(c.linkId, c.evidence, c.reviewer, c.provenance)) ++ prompt.map(
              ReviewPromptRaised(_, c.provenance)
            ) ++
              developed(c.developmentId, DevelopmentKind.EvidenceAccepted, c.linkId, c.provenance)
          )
          .thenReply(s => s)
  }

  def rejectEvidence(c: RejectEvidence): Effect[IssueState] = held {
    Issue.checkMove(currentState, id, c.linkId, EvidenceState.Rejected) match
      case Left(r)                                       => refuse(r)
      case Right(l) if l.state == EvidenceState.Rejected => effects.reply(currentState)
      case Right(_) if c.reason.trim.isEmpty =>
        refuse(
          Rules.ContributionReasonRequired
            .refusal("A reason is required.", "contribution" -> c.linkId)
        )
      case Right(_) =>
        effects
          .persist(EvidenceRejected(c.linkId, c.reviewer, c.reason.trim, c.provenance))
          .thenReply(s => s)
  }

  /** A correction supersedes; nothing is removed, and the claims deriving from it are flagged. */
  def supersedeEvidence(c: SupersedeEvidence): Effect[IssueState] = held {
    Issue.checkMove(currentState, id, c.linkId, EvidenceState.Superseded) match
      case Left(r)                                         => refuse(r)
      case Right(l) if l.state == EvidenceState.Superseded => effects.reply(currentState)
      case Right(_)
          if c.by == c.linkId || !currentState
            .link(c.by)
            .exists(_.state == EvidenceState.Accepted) =>
        refuse(
          Rules.EvidenceReferenceUnresolved.refusal(
            "Evidence is superseded by other accepted evidence on this issue.",
            "kind" -> "evidence",
            "id"   -> c.by
          )
        )
      case Right(l) =>
        val claims = currentState.claimsDerivingFrom(l)
        val prompt = Option.when(claims.nonEmpty)(
          ReviewPrompt(c.promptId, c.linkId, "superseded", claims, Vector.empty, c.provenance.at)
        )
        effects
          .persistAll(
            Seq(
              EvidenceSuperseded(c.linkId, c.by, c.reviewer, c.reason.trim, c.provenance)
            ) ++ prompt.map(ReviewPromptRaised(_, c.provenance))
          )
          .thenReply(s => s)
  }

  /** Resolving records the note and changes nothing else; a second resolution answers unchanged. */
  def resolvePrompt(c: ResolvePrompt): Effect[IssueState] = held {
    currentState.prompt(c.promptId) match
      case None =>
        refuse(
          Rules.ReviewPromptNotOpen
            .refusal("There is no such review prompt.", "issue" -> id, "prompt" -> c.promptId)
        )
      case Some(p) if p.resolved.isDefined => effects.reply(currentState)
      case Some(_) =>
        effects
          .persist(ReviewPromptResolved(c.promptId, c.by, c.note.trim, c.provenance))
          .thenReply(s => s)
  }

  /** An option already on the Issue answers unchanged: its description lives on the option. */
  def addOption(c: AddOption): Effect[IssueState] = held {
    if currentState.options.contains(c.optionId) then effects.reply(currentState)
    else effects.persist(OptionAdded(c.optionId, c.title.trim, c.provenance)).thenReply(s => s)
  }

  // ── investigation and action (US5): written after the record, idempotent on its id ──

  def linkInvestigation(c: LinkInvestigation): Effect[IssueState] = held {
    if currentState.investigations.contains(c.investigationId) then effects.reply(currentState)
    else
      effects
        .persist(InvestigationOpened(c.investigationId, c.question, c.provenance))
        .thenReply(s => s)
  }

  def linkAction(c: LinkAction): Effect[IssueState] = held {
    if currentState.actions.contains(c.actionId) then effects.reply(currentState)
    else
      effects
        .persistAll(
          Seq(ActionRecorded(c.actionId, c.summary, c.developmentId, c.provenance)) ++ developed(
            c.developmentId,
            DevelopmentKind.ActionRecorded,
            c.actionId,
            c.provenance
          )
        )
        .thenReply(s => s)
  }

  def linkDecision(c: LinkDecision): Effect[IssueState] = held {
    if currentState.decisions.contains(c.decisionId) then effects.reply(currentState)
    else
      effects
        .persistAll(
          Seq(
            DecisionRecorded(c.decisionId, c.approach, c.developmentId, c.provenance)
          ) ++ developed(
            c.developmentId,
            DevelopmentKind.DecisionRecorded,
            c.decisionId,
            c.provenance
          )
        )
        .thenReply(s => s)
  }

  def linkOutcome(c: LinkOutcome): Effect[IssueState] = held {
    if currentState.outcomes.contains(c.outcomeId) then effects.reply(currentState)
    else
      effects
        .persistAll(
          Seq(OutcomeRecorded(c.outcomeId, c.actionId, c.developmentId, c.provenance)) ++ developed(
            c.developmentId,
            DevelopmentKind.OutcomeRecorded,
            c.outcomeId,
            c.provenance
          )
        )
        .thenReply(s => s)
  }

  // ── developments (US6): one per authoritative event, keyed for its followers' updates ──

  /** The development an authoritative event mints; none without an id, and none twice. */
  private def developed(
      id: String,
      kind: DevelopmentKind,
      ref: String,
      p: Provenance,
      minor: Boolean = false
  ): Seq[IssueEvent] =
    if id.isEmpty || currentState.development(id).isDefined then Seq.empty
    else Seq(DevelopmentPublished(Development.of(id, kind, ref, p.at, minor), p))

  /** A narrative update, public as written (FR-044); a correction of one is a minor development. */
  def publishUpdate(c: PublishUpdate): Effect[IssueState] = held {
    if currentState.updates.exists(_.developmentId == c.developmentId) then
      effects.reply(currentState)
    else
      Development.checkUpdate(currentState, id, c.text, c.corrects) match
        case Some(r) => refuse(r)
        case None =>
          effects
            .persistAll(
              Seq(UpdatePublished(c.developmentId, c.text.trim, c.by, c.corrects, c.provenance)) ++
                developed(
                  c.developmentId,
                  DevelopmentKind.UpdatePublished,
                  c.developmentId,
                  c.provenance,
                  minor = c.corrects.isDefined
                )
            )
            .thenReply(s => s)
  }

  def get: ReadOnlyEffect[IssueState] = effects.reply(currentState)

object IssueEntity
    extends EventSourcedEntity.Companion[IssueEntity, IssueState, IssueEvent](
      componentId = ComponentId("issue"),
      stateSerializer = Codecs.serializer[IssueState]("issue"),
      eventSerializer = Codecs.serializer[IssueEvent]("issue-event")
    ):
  given Serializer[OpenIssue]       = Codecs.serializer[OpenIssue]("issue-open")
  given Serializer[PublishCopy]     = Codecs.serializer[PublishCopy]("issue-publish")
  given Serializer[WithdrawCopy]    = Codecs.serializer[WithdrawCopy]("issue-withdraw")
  given Serializer[LinkQuestion]    = Codecs.serializer[LinkQuestion]("issue-link-question")
  given Serializer[LinkClaim]       = Codecs.serializer[LinkClaim]("issue-link-claim")
  given Serializer[ProposeEvidence] = Codecs.serializer[ProposeEvidence]("issue-propose-evidence")
  given Serializer[AcceptEvidence]  = Codecs.serializer[AcceptEvidence]("issue-accept-evidence")
  given Serializer[RejectEvidence]  = Codecs.serializer[RejectEvidence]("issue-reject-evidence")
  given Serializer[SupersedeEvidence] =
    Codecs.serializer[SupersedeEvidence]("issue-supersede-evidence")
  given Serializer[ResolvePrompt] = Codecs.serializer[ResolvePrompt]("issue-resolve-prompt")
  given Serializer[AddOption]     = Codecs.serializer[AddOption]("issue-add-option")
  given Serializer[LinkInvestigation] =
    Codecs.serializer[LinkInvestigation]("issue-link-investigation")
  given Serializer[LinkAction]    = Codecs.serializer[LinkAction]("issue-link-action")
  given Serializer[LinkDecision]  = Codecs.serializer[LinkDecision]("issue-link-decision")
  given Serializer[LinkOutcome]   = Codecs.serializer[LinkOutcome]("issue-link-outcome")
  given Serializer[PublishUpdate] = Codecs.serializer[PublishUpdate]("issue-publish-update")
  given Serializer[MarkAuthorErased] =
    Codecs.serializer[MarkAuthorErased]("issue-mark-author-erased")

  def create(context: EventSourcedEntityContext) = new IssueEntity(context)

  val open              = command("open")(_.open)
  val publish           = command("publish")(_.publish)
  val withdraw          = command("withdraw")(_.withdraw)
  val linkQuestion      = command("link-question")(_.linkQuestion)
  val linkClaim         = command("link-claim")(_.linkClaim)
  val proposeEvidence   = command("propose-evidence")(_.proposeEvidence)
  val acceptEvidence    = command("accept-evidence")(_.acceptEvidence)
  val rejectEvidence    = command("reject-evidence")(_.rejectEvidence)
  val supersedeEvidence = command("supersede-evidence")(_.supersedeEvidence)
  val resolvePrompt     = command("resolve-prompt")(_.resolvePrompt)
  val addOption         = command("add-option")(_.addOption)
  val linkInvestigation = command("link-investigation")(_.linkInvestigation)
  val linkAction        = command("link-action")(_.linkAction)
  val linkDecision      = command("link-decision")(_.linkDecision)
  val linkOutcome       = command("link-outcome")(_.linkOutcome)
  val publishUpdate     = command("publish-update")(_.publishUpdate)
  val markAuthorErased  = command("mark-author-erased")(_.markAuthorErased)
  val get               = query("get")(_.get)

/** The public listing of Issues: public columns only, the counts with their caveat (FR-026). */
final case class IssueRow(
    issueId: String,
    title: String,
    summary: String,
    counts: IssueCounts,
    openedAt: Option[java.time.Instant],
    lastDevelopmentAt: Option[java.time.Instant]
)

final class IssueRowsView extends View[IssueEvent, IssueRow]:
  private def update(at: java.time.Instant)(f: IssueRow => IssueRow): Effect =
    rowState.fold(effects.ignore())(r => effects.updateRow(f(r).copy(lastDevelopmentAt = Some(at))))

  def onChange(event: IssueEvent): Effect = event match
    case IssueOpened(title, summary, p) =>
      effects.updateRow(
        IssueRow(updateContext.subject, title, summary, IssueCounts(), Some(p.at), Some(p.at))
      )
    case ContributionPublished(_, _, p) =>
      update(p.at)(r => r.copy(counts = r.counts.copy(contributions = r.counts.contributions + 1)))
    case ContributionAttributed(_, _, _, p) =>
      update(p.at)(r => r.copy(counts = r.counts.copy(contributions = r.counts.contributions + 1)))
    case ContributionRestricted(_, _, p) =>
      update(p.at)(r => r.copy(counts = r.counts.copy(contributions = r.counts.contributions + 1)))
    case ContributionWithdrawn(_, p) =>
      update(p.at)(r =>
        r.copy(counts =
          r.counts
            .copy(contributions = r.counts.contributions - 1, withdrawn = r.counts.withdrawn + 1)
        )
      )
    case QuestionLinked(_, p)              => update(p.at)(identity)
    case ClaimLinked(_, p)                 => update(p.at)(identity)
    case EvidenceAccepted(_, _, _, p)      => update(p.at)(identity)
    case EvidenceSuperseded(_, _, _, _, p) => update(p.at)(identity)
    case OptionAdded(_, _, p)              => update(p.at)(identity)
    case InvestigationOpened(_, _, p)      => update(p.at)(identity)
    case ActionRecorded(_, _, _, p)        => update(p.at)(identity)
    case DecisionRecorded(_, _, _, p)      => update(p.at)(identity)
    case OutcomeRecorded(_, _, _, p)       => update(p.at)(identity)
    case UpdatePublished(_, _, _, _, p)    => update(p.at)(identity)
    case DevelopmentPublished(_, _)        => effects.ignore()
    case AuthorErased(_, cause, p) =>
      update(p.at)(r =>
        r.copy(counts = cause match
          case ErasureCause.Requested =>
            r.counts.copy(erasedRequested = r.counts.erasedRequested + 1)
          case ErasureCause.Dormancy => r.counts.copy(erasedDormancy = r.counts.erasedDormancy + 1))
      )
    case EvidenceProposed(_, _) | EvidenceRejected(_, _, _, _) | ReviewPromptRaised(_, _) |
        ReviewPromptResolved(_, _, _, _) =>
      effects.ignore()

object IssueRows
    extends View.Companion[IssueRowsView, IssueEvent, IssueRow](
      componentId = ComponentId("issue-rows"),
      source = ChangeSource.eventsOf(IssueEntity),
      rowSerializer = Codecs.serializer[IssueRow]("issue-row")
    ):
  def create(ctx: ViewComponentContext) = new IssueRowsView
