package voice.application.disclosure

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.disclosure.*
import voice.domain.disclosure.ContributionEvent.*

final case class Authorise(
    participantId: String,
    concernId: String,
    grant: DisclosureGrant,
    attribution: Option[Sealed[String]],
    provenance: Provenance
)
final case class Submit(participantId: String, provenance: Provenance)
final case class RecordFlags(
    flags: Vector[PrivacyFlag],
    reasons: Vector[String],
    provenance: Provenance
)
final case class RecordEnhanced(reviewer: String, reason: String, provenance: Provenance)
final case class Approve(reviewer: String, issueId: String, pub: String, provenance: Provenance)
final case class Reject(reviewer: String, reason: String, provenance: Provenance)
final case class Associate(
    participantId: String,
    issueId: Option[String],
    newIssueAsked: Boolean,
    provenance: Provenance
)
final case class Withdraw(participantId: String, provenance: Provenance)

/**
 * One disclosure of a confirmed concern (FR-011 to FR-018). It holds the exact approved text
 * sealed, the grant that approved it, and every decision about it; the rules of sharing live here
 * (R12). Every command but `get` names the participant or the reviewer acting.
 */
final class ContributionEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[ContributionRecord, ContributionEvent]:

  private def id = context.entityId

  def emptyState: ContributionRecord = ContributionRecord.empty

  def applyEvent(event: ContributionEvent): ContributionRecord = Contribution(currentState, event)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  private def owned(participant: String): Option[Refusal] =
    Contribution.checkOwner(currentState, id, participant)

  def authorise(c: Authorise): Effect[ContributionRecord] =
    owned(c.participantId).orElse(Contribution.checkAuthorisable(currentState, id)) match
      case Some(r) => refuse(r)
      case None =>
        effects
          .persist(
            DisclosureAuthorised(c.participantId, c.concernId, c.grant, c.attribution, c.provenance)
          )
          .thenReply(s => s)

  /** A repeated submission answers the one under moderation (R7). */
  def submit(c: Submit): Effect[ContributionRecord] =
    owned(c.participantId) match
      case Some(r) => refuse(r)
      case None if currentState.state == ContributionState.UnderModeration =>
        effects.reply(currentState)
      case None =>
        Contribution.checkSubmittable(currentState, id) match
          case Some(r) => refuse(r)
          case None    => effects.persist(SubmittedForReview(c.provenance)).thenReply(s => s)

  def recordFlags(c: RecordFlags): Effect[ContributionRecord] =
    if currentState.state != ContributionState.UnderModeration then effects.reply(currentState)
    else
      effects
        .persist(FlagsRecorded(c.flags.distinct, c.reasons.distinct, c.provenance))
        .thenReply(s => s)

  def recordEnhanced(c: RecordEnhanced): Effect[ContributionRecord] =
    Contribution
      .checkUnderReview(currentState, id)
      .orElse(Contribution.checkReason(c.reason, id)) match
      case Some(r) => refuse(r)
      case None =>
        val step =
          SecondStep(c.reviewer, c.reason.trim, c.provenance.correlationId, c.provenance.at)
        effects.persist(EnhancedReviewRecorded(step, c.provenance)).thenReply(s => s)

  def approve(c: Approve): Effect[ContributionRecord] =
    if currentState.state == ContributionState.Published && currentState.pub.contains(c.pub) then
      effects.reply(currentState)
    else
      Contribution.checkApprovable(currentState, id, c.provenance.correlationId) match
        case Some(r) => refuse(r)
        case None =>
          effects
            .persist(PublicationApproved(c.reviewer, c.issueId, c.pub, c.provenance))
            .thenReply(s => s)

  def reject(c: Reject): Effect[ContributionRecord] =
    if currentState.state == ContributionState.Rejected then effects.reply(currentState)
    else
      Contribution
        .checkUnderReview(currentState, id)
        .orElse(Contribution.checkReason(c.reason, id)) match
        case Some(r) => refuse(r)
        case None =>
          effects
            .persist(PublicationRejected(c.reviewer, c.reason.trim, c.provenance))
            .thenReply(s => s)

  def associate(c: Associate): Effect[ContributionRecord] =
    owned(c.participantId).orElse(Contribution.checkAssociable(currentState, id)) match
      case Some(r) => refuse(r)
      case None =>
        effects
          .persist(
            AssociatedWithIssue(c.issueId, c.newIssueAsked && c.issueId.isEmpty, c.provenance)
          )
          .thenReply(s => s)

  def withdraw(c: Withdraw): Effect[ContributionRecord] =
    owned(c.participantId) match
      case Some(r) => refuse(r)
      case None if currentState.state == ContributionState.Withdrawn || !currentState.exists =>
        effects.reply(currentState)
      case None => effects.persist(DisclosureWithdrawn(c.provenance)).thenReply(s => s)

  def delete: Effect[Done] =
    if !currentState.exists then effects.reply(Done)
    else effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[ContributionRecord] = effects.reply(currentState)

object ContributionEntity
    extends EventSourcedEntity.Companion[ContributionEntity, ContributionRecord, ContributionEvent](
      componentId = ComponentId("contribution"),
      stateSerializer = Codecs.serializer[ContributionRecord]("contribution"),
      eventSerializer = Codecs.serializer[ContributionEvent]("contribution-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[Authorise]      = Codecs.serializer[Authorise]("contribution-authorise")
  given Serializer[Submit]         = Codecs.serializer[Submit]("contribution-submit")
  given Serializer[RecordFlags]    = Codecs.serializer[RecordFlags]("contribution-flags")
  given Serializer[RecordEnhanced] = Codecs.serializer[RecordEnhanced]("contribution-enhanced")
  given Serializer[Approve]        = Codecs.serializer[Approve]("contribution-approve")
  given Serializer[Reject]         = Codecs.serializer[Reject]("contribution-reject")
  given Serializer[Associate]      = Codecs.serializer[Associate]("contribution-associate")
  given Serializer[Withdraw]       = Codecs.serializer[Withdraw]("contribution-withdraw")

  def create(context: EventSourcedEntityContext) = new ContributionEntity(context)

  val authorise      = command("authorise")(_.authorise)
  val submit         = command("submit")(_.submit)
  val recordFlags    = command("record-flags")(_.recordFlags)
  val recordEnhanced = command("record-enhanced")(_.recordEnhanced)
  val approve        = command("approve")(_.approve)
  val reject         = command("reject")(_.reject)
  val associate      = command("associate")(_.associate)
  val withdraw       = command("withdraw")(_.withdraw)
  val delete         = command("delete")(_.delete)
  val get            = query("get")(_.get)

/** The reviewer's queue: state, mode and flags, links, no text (FR-014). */
final case class ContributionRow(
    contributionId: String,
    participantId: String,
    concernId: String,
    mode: String,
    state: String,
    flags: Vector[String],
    associatedIssue: Option[String],
    newIssueAsked: Boolean,
    createdAt: Option[java.time.Instant],
    submittedAt: Option[java.time.Instant]
)

final class ContributionRowsView extends View[ContributionEvent, ContributionRow]:
  private def update(f: ContributionRow => ContributionRow): Effect =
    rowState.fold(effects.ignore())(r => effects.updateRow(f(r)))

  def onChange(event: ContributionEvent): Effect = event match
    case DisclosureAuthorised(participant, concern, grant, _, p) =>
      val base = rowState.getOrElse(
        ContributionRow(
          updateContext.subject,
          participant,
          concern,
          grant.mode.wire,
          "",
          Vector.empty,
          None,
          false,
          Some(p.at),
          None
        )
      )
      val state =
        if grant.mode == DisclosureMode.Private then ContributionState.Drafting
        else ContributionState.AuthorisedByCitizen
      effects.updateRow(
        base.copy(
          mode = grant.mode.wire,
          state = state.toString,
          flags = Vector.empty,
          submittedAt = None
        )
      )
    case SubmittedForReview(p) =>
      update(_.copy(state = ContributionState.UnderModeration.toString, submittedAt = Some(p.at)))
    case FlagsRecorded(flags, _, _)   => update(_.copy(flags = flags.map(_.toString)))
    case EnhancedReviewRecorded(_, _) => effects.ignore()
    case PublicationApproved(_, _, _, _) =>
      update(_.copy(state = ContributionState.Published.toString))
    case PublicationRejected(_, _, _) => update(_.copy(state = ContributionState.Rejected.toString))
    case AssociatedWithIssue(issue, asked, _) =>
      update(_.copy(associatedIssue = issue, newIssueAsked = asked))
    case DisclosureWithdrawn(_) => update(_.copy(state = ContributionState.Withdrawn.toString))

object ContributionRows
    extends View.Companion[ContributionRowsView, ContributionEvent, ContributionRow](
      componentId = ComponentId("contribution-rows"),
      source = ChangeSource.eventsOf(ContributionEntity),
      rowSerializer = Codecs.serializer[ContributionRow]("contribution-row")
    ):
  val inState =
    query("in-state")(s"SELECT payload FROM $table WHERE payload::jsonb->>'state' = :state")
  def create(ctx: ViewComponentContext) = new ContributionRowsView
