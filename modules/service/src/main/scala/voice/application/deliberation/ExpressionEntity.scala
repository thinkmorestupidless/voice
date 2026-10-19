package voice.application.deliberation

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.deliberation.*
import voice.domain.deliberation.ExpressionEvent.*

final case class ExpressPriority(
    issueId: String,
    participantId: String,
    level: Int,
    priority: Sealed[Priority],
    provenance: Provenance
)
final case class ExpressTradeOff(
    issueId: String,
    participantId: String,
    offered: Set[String],
    entry: TradeOffEntry,
    provenance: Provenance
)
final case class RecordConsideration(
    issueId: String,
    participantId: String,
    offered: Set[String],
    entry: ConsiderationEntry,
    provenance: Provenance
)
final case class WithdrawExpression(provenance: Provenance)

/**
 * One participant's statements on one Issue (FR-030 to FR-032), id `exp-…` keyed on the pair so the
 * participant finds it again without a lookup. A priority after a withdrawal starts again; a
 * trade-off or consideration on a withdrawn expression is refused (`expression.withdrawn`).
 */
final class ExpressionEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[ExpressionState, ExpressionEvent]:

  def emptyState: ExpressionState = ExpressionState.empty

  def applyEvent(event: ExpressionEvent): ExpressionState = Expression(currentState, event)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def priority(c: ExpressPriority): Effect[ExpressionState] =
    Expression.checkLevel(c.issueId, c.level) match
      case Some(r) => refuse(r)
      case None =>
        val event =
          if currentState.level.isDefined && !currentState.withdrawn then
            ExpressionAmended(c.level, c.priority, c.provenance)
          else PriorityExpressed(c.issueId, c.participantId, c.level, c.priority, c.provenance)
        effects.persist(event).thenReply(s => s)

  def tradeOff(c: ExpressTradeOff): Effect[ExpressionState] =
    Expression
      .checkLive(currentState, c.issueId)
      .orElse(Expression.checkOption(c.issueId, c.offered, c.entry.optionId)) match
      case Some(r) => refuse(r)
      case None =>
        effects
          .persist(TradeOffExpressed(c.issueId, c.participantId, c.entry, c.provenance))
          .thenReply(s => s)

  def consider(c: RecordConsideration): Effect[ExpressionState] =
    Expression
      .checkLive(currentState, c.issueId)
      .orElse(Expression.checkOption(c.issueId, c.offered, c.entry.optionId)) match
      case Some(r) => refuse(r)
      case None =>
        effects
          .persist(ConsiderationRecorded(c.issueId, c.participantId, c.entry, c.provenance))
          .thenReply(s => s)

  /** A second withdrawal answers unchanged. */
  def withdraw(c: WithdrawExpression): Effect[ExpressionState] =
    if !currentState.exists || currentState.withdrawn then effects.reply(currentState)
    else effects.persist(ExpressionWithdrawn(c.provenance)).thenReply(s => s)

  /** Removes the expression for good: the erasure workflow's purge (FR-055). */
  def delete: Effect[Done] =
    if !currentState.exists then effects.reply(Done)
    else effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[ExpressionState] = effects.reply(currentState)

object ExpressionEntity
    extends EventSourcedEntity.Companion[ExpressionEntity, ExpressionState, ExpressionEvent](
      componentId = ComponentId("expression"),
      stateSerializer = Codecs.serializer[ExpressionState]("expression"),
      eventSerializer = Codecs.serializer[ExpressionEvent]("expression-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[ExpressPriority] = Codecs.serializer[ExpressPriority]("expression-priority")
  given Serializer[ExpressTradeOff] = Codecs.serializer[ExpressTradeOff]("expression-trade-off")
  given Serializer[RecordConsideration] =
    Codecs.serializer[RecordConsideration]("expression-consideration")
  given Serializer[WithdrawExpression] =
    Codecs.serializer[WithdrawExpression]("expression-withdraw")

  def create(context: EventSourcedEntityContext) = new ExpressionEntity(context)

  val priority = command("priority")(_.priority)
  val tradeOff = command("trade-off")(_.tradeOff)
  val consider = command("consider")(_.consider)
  val withdraw = command("withdraw")(_.withdraw)
  val delete   = command("delete")(_.delete)
  val get      = query("get")(_.get)

/**
 * What the aggregate counts, one row per expression (FR-033): the level, the stances per option and
 * the options considered, with no participant and no words. A withdrawn expression's row is
 * deleted.
 */
final class IssueAggregatesView extends View[ExpressionEvent, AggregateRow]:

  private def row(issue: String, at: java.time.Instant): AggregateRow =
    rowState.getOrElse(
      AggregateRow(updateContext.subject, issue, None, Vector.empty, Vector.empty, at, at)
    )

  def onChange(event: ExpressionEvent): Effect = event match
    case PriorityExpressed(issue, _, level, _, p) =>
      effects.updateRow(row(issue, p.at).copy(level = Some(level), lastAt = p.at))
    case ExpressionAmended(level, _, p) =>
      rowState.fold(effects.ignore())(r =>
        effects.updateRow(r.copy(level = Some(level), lastAt = p.at))
      )
    case TradeOffExpressed(issue, _, entry, p) =>
      val r = row(issue, p.at)
      effects.updateRow(
        r.copy(
          stances = r.stances
            .filterNot(_.optionId == entry.optionId) :+ OptionStance(entry.optionId, entry.stance),
          lastAt = p.at
        )
      )
    case ConsiderationRecorded(issue, _, entry, p) =>
      val r = row(issue, p.at)
      effects.updateRow(r.copy(considerations = r.considerations :+ entry.optionId, lastAt = p.at))
    case ExpressionWithdrawn(_) => effects.deleteRow()

object IssueAggregates
    extends View.Companion[IssueAggregatesView, ExpressionEvent, AggregateRow](
      componentId = ComponentId("issue-aggregates"),
      source = ChangeSource.eventsOf(ExpressionEntity),
      rowSerializer = Codecs.serializer[AggregateRow]("issue-aggregate-row")
    ):
  val forIssue =
    query("for-issue")(s"SELECT payload FROM $table WHERE payload::jsonb->>'issueId' = :issue")
  def create(ctx: ViewComponentContext) = new IssueAggregatesView
