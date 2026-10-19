package voice.application.erasure

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.deliberation.ExpressionEntity
import voice.application.disclosure.{ContributionEntity, Withdraw}
import voice.application.interests.*
import voice.application.issues.*
import voice.application.participation.*
import voice.domain.*

import voice.domain.erasure.*
import voice.domain.interests.Interest
import voice.sealing.Sealing

import java.time.Instant
import scala.concurrent.duration.DurationInt

final case class StartErasure(
    cause: ErasureCause,
    verification: Verification,
    provenance: Provenance
)
final case class VerifyErasure(principal: String, at: Instant)

/**
 * One participant's erasure, id = the participant (R1, FR-052 to FR-058). Every step is safe to
 * repeat: a restart part way resumes at the step it was in, and each step's calls are idempotent. A
 * request no session could verify pauses before `inventory` until an operator verifies it; a
 * dormancy erasure needs no verification.
 *
 * Order matters: interests are revoked before the key goes, so no update is prepared for someone
 * half erased; public copies are settled before the key goes, while the copy can still be opened to
 * decide; the key goes before the purge, so a record the purge has not reached yet is already
 * unreadable.
 */
final class ErasureWorkflow(context: WorkflowContext) extends Workflow[ErasureRecord]:

  def emptyState: ErasureRecord = ErasureRecord.empty

  private def participant = currentState.participantId
  private def cause       = currentState.cause.getOrElse(ErasureCause.Requested)
  private def client      = context.componentClient

  private def provenance(at: Instant) =
    Provenance(Actor.System("erasure"), at, s"erasure:$participant", s"erasure:$participant")

  private def at(): Instant = currentState.requestedAt.getOrElse(Instant.EPOCH)

  /** A second request, or a dormancy firing for an erasure already running, answers unchanged. */
  def start(c: StartErasure): Effect[ErasureRecord] =
    if currentState.started then effects.reply(currentState)
    else
      val stage =
        if c.verification.verified then ErasureStage.InProgress else ErasureStage.HeldForOperator
      val state = ErasureRecord.empty.copy(
        participantId = context.workflowId,
        cause = Some(c.cause),
        verification = c.verification,
        stage = stage,
        requestedAt = Some(c.provenance.at)
      )
      effects.updateState(state).transitionTo(ErasureWorkflow.verify.ref).thenReply(state)

  /** An operator verifies a held request; anything else answers unchanged. */
  def operatorVerify(c: VerifyErasure): Effect[ErasureRecord] =
    if currentState.stage != ErasureStage.HeldForOperator then effects.reply(currentState)
    else
      val state = currentState.copy(
        verification = Verification.OperatorVerified(c.principal, c.at),
        stage = ErasureStage.InProgress
      )
      effects.updateState(state).transitionTo(ErasureWorkflow.inventory.ref).thenReply(state)

  def status: ReadOnlyEffect[ErasureRecord] = effects.reply(currentState)

  // ── steps ──────────────────────────────────────────────────────────────────

  /** The participant is marked as requested; a held request waits for an operator here. */
  def verifyStep: StepEffect =
    client
      .forEventSourcedEntity(EntityId(participant))
      .call(ParticipantEntity.requestErasure)
      .invoke(MarkErasure(cause, provenance(at()))): Unit
    val marked = currentState.mark(ErasureStep.Verify, at())
    if currentState.verification.verified then
      stepEffects.updateState(marked).thenTransitionTo(ErasureWorkflow.inventory.ref)
    else stepEffects.updateState(marked).thenPause()

  /** Every record of the participant's, from the index written where each was created. */
  def inventoryStep: StepEffect =
    val found =
      client.forKeyValueEntity(EntityId(participant)).call(ParticipantRecordsEntity.get).invoke()
    stepEffects
      .updateState(
        currentState.copy(scope = currentState.scope ++ found).mark(ErasureStep.Inventory, at())
      )
      .thenTransitionTo(ErasureWorkflow.revoke.ref)

  /**
   * Interests stop and the participant leaves every follower list: nothing new is prepared for
   * them.
   */
  def revokeStep: StepEffect =
    for id <- currentState.scope.interests do
      val issueId = id.stripPrefix(s"$participant:")
      client
        .forKeyValueEntity(EntityId(id))
        .call(InterestEntity.stop)
        .invoke(StopFollowing(participant, at())): Unit
      client
        .forKeyValueEntity(EntityId(issueId))
        .call(IssueFollowersEntity.remove)
        .invoke(RemoveFollower(participant)): Unit
    stepEffects
      .updateState(currentState.mark(ErasureStep.Revoke, at()))
      .thenTransitionTo(ErasureWorkflow.withdraw.ref)

  /**
   * Attributed and restricted copies leave their issues; so does anything not yet published, so a
   * review still open cannot publish it later. De-identified published copies wait for `residue`.
   */
  def withdrawStep: StepEffect =
    var withdrawn = currentState.withdrawn
    for id <- currentState.scope.contributions do
      val c      = client.forEventSourcedEntity(EntityId(id))
      val record = c.call(ContributionEntity.get).invoke()
      val publicCopy =
        record.state == ContributionState.Published && record.mode == DisclosureMode.DeIdentified
      if record.exists && !publicCopy then
        for issueId <- record.issueId; pub <- record.pub do
          client
            .forEventSourcedEntity(EntityId(issueId))
            .call(IssueEntity.withdraw)
            .invoke(WithdrawCopy(pub, provenance(at()))): Unit
          if !withdrawn.contains(pub) then withdrawn = withdrawn :+ pub
        if record.state != ContributionState.Withdrawn then
          c.call(ContributionEntity.withdraw).invoke(Withdraw(participant, provenance(at()))): Unit
    stepEffects
      .updateState(currentState.copy(withdrawn = withdrawn).mark(ErasureStep.Withdraw, at()))
      .thenTransitionTo(ErasureWorkflow.residue.ref)

  /**
   * Each retained de-identified copy stays marked with the cause, or goes if it could identify
   * (FR-057).
   */
  def residueStep: StepEffect =
    var retained = currentState.retained
    for id <- currentState.scope.contributions do
      val record = client.forEventSourcedEntity(EntityId(id)).call(ContributionEntity.get).invoke()
      for
        issueId <- record.issueId; pub <- record.pub
        if record.state == ContributionState.Published && record.mode == DisclosureMode.DeIdentified && !retained
          .exists(_.pub == pub)
      do
        val issue  = client.forEventSourcedEntity(EntityId(issueId))
        val result = Residue.decide(record)
        result match
          case ResidueResult.Retained =>
            issue
              .call(IssueEntity.markAuthorErased)
              .invoke(MarkAuthorErased(pub, cause, provenance(at()))): Unit
          case ResidueResult.Suppressed(_) =>
            issue.call(IssueEntity.withdraw).invoke(WithdrawCopy(pub, provenance(at()))): Unit
        retained = retained :+ RetainedCopy(pub, issueId, result)
    stepEffects
      .updateState(currentState.copy(retained = retained).mark(ErasureStep.Residue, at()))
      .thenTransitionTo(ErasureWorkflow.destroyKey.ref)

  /**
   * From here every value sealed for the participant, in any record or backup, opens as
   * unavailable.
   */
  def destroyKeyStep: StepEffect =
    Sealing(context.secrets).destroyKey(participant)
    stepEffects
      .updateState(currentState.mark(ErasureStep.DestroyKey, at()))
      .thenTransitionTo(ErasureWorkflow.purgeDerived.ref)

  /** The participant's own records go, then the links from public copies, then the index itself. */
  def purgeDerivedStep: StepEffect =
    val again =
      client.forKeyValueEntity(EntityId(participant)).call(ParticipantRecordsEntity.get).invoke()
    val scope          = currentState.scope ++ again
    def es(id: String) = client.forEventSourcedEntity(EntityId(id))
    def kv(id: String) = client.forKeyValueEntity(EntityId(id))
    val pubs = scope.contributions.flatMap(id => es(id).call(ContributionEntity.get).invoke().pub)
    for id <- scope.conversations do es(id).call(ConversationEntity.delete).invoke(): Unit
    for id <- scope.concerns do es(id).call(ConcernEntity.delete).invoke(): Unit
    for id <- scope.contributions do es(id).call(ContributionEntity.delete).invoke(): Unit
    for id <- scope.expressions do es(id).call(ExpressionEntity.delete).invoke(): Unit
    for id <- scope.interests do kv(id).call(InterestEntity.delete).invoke(): Unit
    for id <- scope.notifications do es(id).call(NotificationEntity.delete).invoke(): Unit
    for pub <- (pubs ++ currentState.withdrawn ++ currentState.retained.map(_.pub)).distinct do
      kv(pub).call(IssueLinkEntity.erase).invoke(EraseLink(provenance(at()))): Unit
      kv(pub).call(IssueLinkEntity.delete).invoke(): Unit
    kv(participant).call(ParticipantRecordsEntity.delete).invoke(): Unit
    stepEffects
      .updateState(currentState.copy(scope = scope).mark(ErasureStep.PurgeDerived, at()))
      .thenTransitionTo(ErasureWorkflow.complete.ref)

  /**
   * The participant refuses everything from here; the receipt says what went, and that the key is
   * gone.
   */
  def completeStep: StepEffect =
    val p = provenance(at())
    client
      .forEventSourcedEntity(EntityId(participant))
      .call(ParticipantEntity.completeErasure)
      .invoke(MarkErasure(cause, p)): Unit
    val authority = (cause, currentState.verification) match
      case (ErasureCause.Dormancy, _)                       => Authority.DormancyRule
      case (_, Verification.OperatorVerified(principal, _)) => Authority.Operator(principal)
      case _                                                => Authority.Participant
    val keyGone = !Sealing(context.secrets).hasKey(participant)
    val done    = currentState.mark(ErasureStep.Complete, at())
    stepEffects
      .updateState(
        done.copy(
          stage = ErasureStage.Completed,
          receipt = Some(Receipt.of(done, authority, at(), keyGone))
        )
      )
      .thenEnd

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(60.seconds)
      .defaultRecovery(RecoverStrategy.maxRetries(10))
      .build

object ErasureWorkflow
    extends Workflow.Companion[ErasureWorkflow, ErasureRecord](
      componentId = ComponentId("erasure"),
      stateSerializer = Codecs.serializer[ErasureRecord]("erasure")
    ):
  given Serializer[StartErasure]  = Codecs.serializer[StartErasure]("erasure-start")
  given Serializer[VerifyErasure] = Codecs.serializer[VerifyErasure]("erasure-verify")

  def create(context: WorkflowContext) = new ErasureWorkflow(context)

  val verify       = step("verify")(_.verifyStep)
  val inventory    = step("inventory")(_.inventoryStep)
  val revoke       = step("revoke")(_.revokeStep)
  val withdraw     = step("withdraw")(_.withdrawStep)
  val residue      = step("residue")(_.residueStep)
  val destroyKey   = step("destroyKey")(_.destroyKeyStep)
  val purgeDerived = step("purgeDerived")(_.purgeDerivedStep)
  val complete     = step("complete")(_.completeStep)

  val start          = command("start")(_.start)
  val operatorVerify = command("operator-verify")(_.operatorVerify)
  val status         = query("status")(_.status)

/**
 * The operator's list: one row per erasure, its stage, cause and receipt; no personal value
 * (FR-058).
 */
final case class ErasureRow(
    participantId: String,
    cause: Option[ErasureCause],
    stage: String,
    held: Boolean,
    requestedAt: Option[Instant],
    receipt: Option[Receipt]
)

final class ErasureReceiptsView extends View[ErasureRecord, ErasureRow]:
  def onChange(state: ErasureRecord): Effect =
    if !state.started then effects.ignore()
    else
      effects.updateRow(
        ErasureRow(
          state.participantId,
          state.cause,
          state.stage.wire,
          state.stage == ErasureStage.HeldForOperator,
          state.requestedAt,
          state.receipt
        )
      )

object ErasureReceipts
    extends View.Companion[ErasureReceiptsView, ErasureRecord, ErasureRow](
      componentId = ComponentId("erasure-receipts"),
      source = ChangeSource.stateOf(ErasureWorkflow),
      rowSerializer = Codecs.serializer[ErasureRow]("erasure-row")
    ):
  val inStage =
    query("in-stage")(s"SELECT payload FROM $table WHERE payload::jsonb->>'stage' = :stage")
  def create(ctx: ViewComponentContext) = new ErasureReceiptsView
