package voice.domain.accountability

import voice.domain.*

import java.time.{Instant, LocalDate}

/** A task's state; `awaiting-external` is a follow-up the work queue shows (FR-036). */
enum TaskState(val label: String):
  case Open             extends TaskState("open")
  case AwaitingExternal extends TaskState("awaiting an external party")
  case Done             extends TaskState("done")

object TaskState:
  def parse(s: String): Option[TaskState] =
    values.find(t => t.label == s.trim || t.toString.equalsIgnoreCase(s.trim.replace("-", "")))

final case class Task(
    taskId: String,
    text: String,
    owner: String,
    due: LocalDate,
    state: TaskState,
    at: Instant
)

/** What the research agent read and said, kept beside its suggestion (R15). */
final case class Suggestion(run: String, reason: String, records: Vector[String])

final case class InvestigationRecord(
    investigationId: String,
    issueId: String,
    question: String,
    status: InvestigationState,
    suggestion: Option[Suggestion],
    openedBy: Option[String],
    openedAt: Option[Instant],
    tasks: Vector[Task],
    closedAt: Option[Instant],
    createdAt: Option[Instant]
):
  def exists: Boolean = issueId.nonEmpty

object InvestigationRecord:
  val empty: InvestigationRecord = InvestigationRecord(
    "",
    "",
    "",
    InvestigationState.Suggested,
    None,
    None,
    None,
    Vector.empty,
    None,
    None
  )

enum InvestigationEvent:
  case InvestigationSuggested(
      issueId: String,
      question: String,
      suggestion: Suggestion,
      provenance: Provenance
  )
  case InvestigationOpened(
      issueId: String,
      question: String,
      representative: String,
      provenance: Provenance
  )
  case TaskAdded(task: Task, provenance: Provenance)
  case TaskUpdated(taskId: String, state: TaskState, provenance: Provenance)
  case InvestigationClosed(provenance: Provenance)

object Investigation:
  import InvestigationEvent.*

  def apply(s: InvestigationRecord, e: InvestigationEvent): InvestigationRecord = e match
    case InvestigationSuggested(issue, question, suggestion, p) =>
      s.copy(
        issueId = issue,
        question = question,
        status = InvestigationState.Suggested,
        suggestion = Some(suggestion),
        createdAt = Some(p.at)
      )
    case InvestigationOpened(issue, question, by, p) =>
      s.copy(
        issueId = issue,
        question = question,
        status = InvestigationState.Open,
        openedBy = Some(by),
        openedAt = Some(p.at),
        createdAt = s.createdAt.orElse(Some(p.at))
      )
    case TaskAdded(task, _) => s.copy(tasks = s.tasks :+ task)
    case TaskUpdated(id, state, _) =>
      s.copy(tasks = s.tasks.map(t => if t.taskId == id then t.copy(state = state) else t))
    case InvestigationClosed(p) => s.copy(status = InvestigationState.Closed, closedAt = Some(p.at))

  /** A task is attached only to an investigation a representative opened (FR-039). */
  def checkOpen(s: InvestigationRecord, id: String): Option[Refusal] =
    if !s.exists then
      Some(Rules.IssueNotHeld.refusal("There is no such investigation.", "issue" -> id))
    else
      Option.when(s.status == InvestigationState.Suggested)(
        Rules.InvestigationSuggestedNotApproved.refusal(
          "A suggested investigation must be opened by a representative first.",
          "investigation" -> id
        )
      )
