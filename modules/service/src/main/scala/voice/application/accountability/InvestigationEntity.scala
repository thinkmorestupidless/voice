package voice.application.accountability

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.accountability.*
import voice.domain.accountability.InvestigationEvent.*

final case class SuggestInvestigation(
    issueId: String,
    question: String,
    suggestion: Suggestion,
    provenance: Provenance
)
final case class OpenInvestigation(
    issueId: String,
    question: String,
    representative: String,
    provenance: Provenance
)
final case class AddTask(task: Task, provenance: Provenance)
final case class UpdateTask(taskId: String, state: TaskState, provenance: Provenance)
final case class CloseInvestigation(provenance: Provenance)

/**
 * An investigation, id `inv-…` (FR-038, FR-039). The research workflow suggests; only a
 * representative opens, and opening a suggestion is the approval. A task needs an open one.
 */
final class InvestigationEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[InvestigationRecord, InvestigationEvent]:

  def emptyState: InvestigationRecord =
    InvestigationRecord.empty.copy(investigationId = context.entityId)

  def applyEvent(event: InvestigationEvent): InvestigationRecord =
    Investigation(currentState, event).copy(investigationId = context.entityId)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def suggest(c: SuggestInvestigation): Effect[InvestigationRecord] =
    if currentState.exists then effects.reply(currentState)
    else
      effects
        .persist(InvestigationSuggested(c.issueId, c.question.trim, c.suggestion, c.provenance))
        .thenReply(s => s)

  def open(c: OpenInvestigation): Effect[InvestigationRecord] =
    if currentState.status == InvestigationState.Open || currentState.status == InvestigationState.Closed
    then effects.reply(currentState)
    else
      val question = if c.question.trim.nonEmpty then c.question.trim else currentState.question
      if question.isEmpty then refuse(Action.checkFields(context.entityId, "question" -> "").get)
      else
        effects
          .persist(
            InvestigationOpened(
              if currentState.issueId.nonEmpty then currentState.issueId else c.issueId,
              question,
              c.representative,
              c.provenance
            )
          )
          .thenReply(s => s)

  def addTask(c: AddTask): Effect[InvestigationRecord] =
    Investigation
      .checkOpen(currentState, context.entityId)
      .orElse(
        Action.checkFields(context.entityId, "text" -> c.task.text, "owner" -> c.task.owner)
      ) match
      case Some(r) => refuse(r)
      case None if currentState.tasks.exists(_.taskId == c.task.taskId) =>
        effects.reply(currentState)
      case None => effects.persist(TaskAdded(c.task, c.provenance)).thenReply(s => s)

  def updateTask(c: UpdateTask): Effect[InvestigationRecord] =
    Investigation.checkOpen(currentState, context.entityId) match
      case Some(r) => refuse(r)
      case None if !currentState.tasks.exists(_.taskId == c.taskId) =>
        refuse(
          Rules.IssueNotHeld.refusal("There is no such task.", "issue" -> currentState.issueId)
        )
      case None => effects.persist(TaskUpdated(c.taskId, c.state, c.provenance)).thenReply(s => s)

  def close(c: CloseInvestigation): Effect[InvestigationRecord] =
    Investigation.checkOpen(currentState, context.entityId) match
      case Some(r)                                                  => refuse(r)
      case None if currentState.status == InvestigationState.Closed => effects.reply(currentState)
      case None => effects.persist(InvestigationClosed(c.provenance)).thenReply(s => s)

  def get: ReadOnlyEffect[InvestigationRecord] = effects.reply(currentState)

object InvestigationEntity
    extends EventSourcedEntity.Companion[
      InvestigationEntity,
      InvestigationRecord,
      InvestigationEvent
    ](
      componentId = ComponentId("investigation"),
      stateSerializer = Codecs.serializer[InvestigationRecord]("investigation"),
      eventSerializer = Codecs.serializer[InvestigationEvent]("investigation-event")
    ):
  given Serializer[SuggestInvestigation] =
    Codecs.serializer[SuggestInvestigation]("investigation-suggest")
  given Serializer[OpenInvestigation] = Codecs.serializer[OpenInvestigation]("investigation-open")
  given Serializer[AddTask]           = Codecs.serializer[AddTask]("investigation-add-task")
  given Serializer[UpdateTask]        = Codecs.serializer[UpdateTask]("investigation-update-task")
  given Serializer[CloseInvestigation] =
    Codecs.serializer[CloseInvestigation]("investigation-close")

  def create(context: EventSourcedEntityContext) = new InvestigationEntity(context)

  val suggest    = command("suggest")(_.suggest)
  val open       = command("open")(_.open)
  val addTask    = command("add-task")(_.addTask)
  val updateTask = command("update-task")(_.updateTask)
  val close      = command("close")(_.close)
  val get        = query("get")(_.get)

/**
 * One row per investigation, for the work queue (FR-036): state, ages, and tasks awaiting others.
 */
final case class InvestigationRow(
    investigationId: String,
    issueId: String,
    question: String,
    status: String,
    createdAt: java.time.Instant,
    openedAt: Option[java.time.Instant],
    awaitingExternal: Vector[String]
)

final class InvestigationRowsView extends View[InvestigationEvent, InvestigationRow]:
  def onChange(event: InvestigationEvent): Effect =
    def update(f: InvestigationRow => InvestigationRow) =
      rowState.fold(effects.ignore())(r => effects.updateRow(f(r)))
    event match
      case InvestigationSuggested(issue, question, _, p) =>
        effects.updateRow(
          InvestigationRow(
            updateContext.subject,
            issue,
            question,
            InvestigationState.Suggested.toString,
            p.at,
            None,
            Vector.empty
          )
        )
      case InvestigationOpened(issue, question, _, p) =>
        effects.updateRow(
          rowState
            .getOrElse(
              InvestigationRow(updateContext.subject, issue, question, "", p.at, None, Vector.empty)
            )
            .copy(
              status = InvestigationState.Open.toString,
              openedAt = Some(p.at),
              question = question
            )
        )
      case TaskAdded(task, _) =>
        update(r =>
          if task.state == TaskState.AwaitingExternal then
            r.copy(awaitingExternal = r.awaitingExternal :+ task.taskId)
          else r
        )
      case TaskUpdated(id, state, _) =>
        update(r =>
          r.copy(awaitingExternal =
            if state == TaskState.AwaitingExternal then (r.awaitingExternal :+ id).distinct
            else r.awaitingExternal.filterNot(_ == id)
          )
        )
      case InvestigationClosed(_) =>
        update(_.copy(status = InvestigationState.Closed.toString, awaitingExternal = Vector.empty))

object InvestigationRows
    extends View.Companion[InvestigationRowsView, InvestigationEvent, InvestigationRow](
      componentId = ComponentId("investigation-rows"),
      source = ChangeSource.eventsOf(InvestigationEntity),
      rowSerializer = Codecs.serializer[InvestigationRow]("investigation-row")
    ):
  val forIssue =
    query("for-issue")(s"SELECT payload FROM $table WHERE payload::jsonb->>'issueId' = :issue")
  val notClosed =
    query("not-closed")(s"SELECT payload FROM $table WHERE payload::jsonb->>'status' <> 'Closed'")
  def create(ctx: ViewComponentContext) = new InvestigationRowsView
