package voice.application.accountability

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.accountability.*

import java.time.LocalDate

final case class RecordAction(
    record: ActionRecord,
    today: LocalDate,
    issueOpened: LocalDate,
    provenance: Provenance
)
final case class ChangeStage(stage: ActionState, provenance: Provenance)
final case class RecordDecision(record: DecisionRecord, provenance: Provenance)
final case class RecordOutcome(
    record: OutcomeRecord,
    actions: Vector[String],
    provenance: Provenance
)

/** A dated, accountable action, id `act-…` (FR-040, FR-041). Its stage moves only forward. */
final class ActionEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[ActionRecord, ActionEvent]:
  import ActionEvent.*

  def emptyState: ActionRecord = ActionRecord.empty

  def applyEvent(event: ActionEvent): ActionRecord = Action(currentState, event)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def record(c: RecordAction): Effect[ActionRecord] =
    if currentState.exists then effects.reply(currentState)
    else
      val r = c.record
      Action
        .checkFields(
          context.entityId,
          "authority"      -> r.authority,
          "owner"          -> r.owner,
          "rationale"      -> r.rationale,
          "public summary" -> r.publicSummary
        )
        .orElse(Action.checkDate(context.entityId, r.date, c.today, r.issueId, c.issueOpened)) match
        case Some(refusal) => refuse(refusal)
        case None =>
          effects
            .persist(ActionRecorded(r.copy(actionId = context.entityId), c.provenance))
            .thenReply(s => s)

  def changeStage(c: ChangeStage): Effect[ActionRecord] =
    if !currentState.exists then
      refuse(
        Rules.OutcomeActionNotHeld
          .refusal("There is no such action.", "issue" -> "", "action" -> context.entityId)
      )
    else if currentState.stage == c.stage then effects.reply(currentState)
    else
      Action.checkForward(context.entityId, currentState.stage, c.stage) match
        case Some(refusal) => refuse(refusal)
        case None => effects.persist(ActionStateChanged(c.stage, c.provenance)).thenReply(s => s)

  def get: ReadOnlyEffect[ActionRecord] = effects.reply(currentState)

object ActionEntity
    extends EventSourcedEntity.Companion[ActionEntity, ActionRecord, ActionEvent](
      componentId = ComponentId("action"),
      stateSerializer = Codecs.serializer[ActionRecord]("action"),
      eventSerializer = Codecs.serializer[ActionEvent]("action-event")
    ):
  given Serializer[RecordAction] = Codecs.serializer[RecordAction]("action-record")
  given Serializer[ChangeStage]  = Codecs.serializer[ChangeStage]("action-stage")

  def create(context: EventSourcedEntityContext) = new ActionEntity(context)

  val record      = command("record")(_.record)
  val changeStage = command("change-stage")(_.changeStage)
  val get         = query("get")(_.get)

/** A decision, id `dec-…` (FR-043). */
final class DecisionEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[DecisionRecord, DecisionEvent]:
  import DecisionEvent.*

  def emptyState: DecisionRecord = DecisionRecord.empty

  def applyEvent(event: DecisionEvent): DecisionRecord = event match
    case DecisionRecorded(r, _) => r

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def record(c: RecordDecision): Effect[DecisionRecord] =
    if currentState.exists then effects.reply(currentState)
    else
      val r = c.record
      Action.checkFields(
        context.entityId,
        "approach"      -> r.approach,
        "authority"     -> r.authority,
        "justification" -> r.justification
      ) match
        case Some(refusal) => refuse(refusal)
        case None =>
          effects
            .persist(DecisionRecorded(r.copy(decisionId = context.entityId), c.provenance))
            .thenReply(s => s)

  def get: ReadOnlyEffect[DecisionRecord] = effects.reply(currentState)

object DecisionEntity
    extends EventSourcedEntity.Companion[DecisionEntity, DecisionRecord, DecisionEvent](
      componentId = ComponentId("decision"),
      stateSerializer = Codecs.serializer[DecisionRecord]("decision"),
      eventSerializer = Codecs.serializer[DecisionEvent]("decision-event")
    ):
  given Serializer[RecordDecision] = Codecs.serializer[RecordDecision]("decision-record")

  def create(context: EventSourcedEntityContext) = new DecisionEntity(context)

  val record = command("record")(_.record)
  val get    = query("get")(_.get)

/** An outcome, id `out-…`, linked to an action on the same Issue (FR-043). */
final class OutcomeEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[OutcomeRecord, OutcomeEvent]:
  import OutcomeEvent.*

  def emptyState: OutcomeRecord = OutcomeRecord.empty

  def applyEvent(event: OutcomeEvent): OutcomeRecord = event match
    case OutcomeRecorded(r, _) => r

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def record(c: RecordOutcome): Effect[OutcomeRecord] =
    if currentState.exists then effects.reply(currentState)
    else
      val r = c.record
      Outcome
        .checkAction(r.issueId, c.actions, r.actionId)
        .orElse(
          Action
            .checkFields(context.entityId, "measured" -> r.measured, "limitations" -> r.limitations)
        ) match
        case Some(refusal) => refuse(refusal)
        case None =>
          effects
            .persist(OutcomeRecorded(r.copy(outcomeId = context.entityId), c.provenance))
            .thenReply(s => s)

  def get: ReadOnlyEffect[OutcomeRecord] = effects.reply(currentState)

object OutcomeEntity
    extends EventSourcedEntity.Companion[OutcomeEntity, OutcomeRecord, OutcomeEvent](
      componentId = ComponentId("outcome"),
      stateSerializer = Codecs.serializer[OutcomeRecord]("outcome"),
      eventSerializer = Codecs.serializer[OutcomeEvent]("outcome-event")
    ):
  given Serializer[RecordOutcome] = Codecs.serializer[RecordOutcome]("outcome-record")

  def create(context: EventSourcedEntityContext) = new OutcomeEntity(context)

  val record = command("record")(_.record)
  val get    = query("get")(_.get)
