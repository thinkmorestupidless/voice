package voice.domain.accountability

import voice.domain.*

import java.time.{Instant, LocalDate}

/** A dated, accountable action a representative recorded (FR-040). */
final case class ActionRecord(
    actionId: String,
    issueId: String,
    investigationId: Option[String],
    authority: String,
    owner: String,
    date: LocalDate,
    rationale: String,
    publicSummary: String,
    stage: ActionState,
    recordedBy: String,
    recordedAt: Instant,
    developmentId: String
):
  def exists: Boolean = actionId.nonEmpty

object ActionRecord:
  val empty: ActionRecord = ActionRecord(
    "",
    "",
    None,
    "",
    "",
    LocalDate.EPOCH,
    "",
    "",
    ActionState.Proposed,
    "",
    Instant.EPOCH,
    ""
  )

enum ActionEvent:
  case ActionRecorded(record: ActionRecord, provenance: Provenance)
  case ActionStateChanged(stage: ActionState, provenance: Provenance)

object Action:
  import ActionEvent.*

  def apply(s: ActionRecord, e: ActionEvent): ActionRecord = e match
    case ActionRecorded(r, _)         => r
    case ActionStateChanged(stage, _) => s.copy(stage = stage)

  /** Every field a reader is owed, named when missing (`action.fields.required`). */
  def checkFields(id: String, fields: (String, String)*): Option[Refusal] =
    val missing = fields.collect { case (name, value) if value.trim.isEmpty => name }
    Option.when(missing.nonEmpty)(
      Rules.ActionFieldsRequired.refusal(
        s"An action needs ${missing.mkString(", ")}.",
        "action"  -> id,
        "missing" -> missing.mkString(",")
      )
    )

  /** A state that is present but not one Voice knows: the same rule, naming the states it takes. */
  def unknownState(id: String, stated: String): Refusal =
    Rules.ActionFieldsRequired.refusal(
      s"An action's state is one of ${ActionState.values.map(_.wire).mkString(", ")}, not '$stated'.",
      "action"  -> id,
      "missing" -> "state"
    )

  /** Not after today, not before the Issue opened (edge case 8). */
  def checkDate(
      id: String,
      date: LocalDate,
      today: LocalDate,
      issueId: String,
      opened: LocalDate
  ): Option[Refusal] =
    if date.isAfter(today) then
      Some(
        Rules.ActionDateFuture
          .refusal("An action is dated today or earlier.", "action" -> id, "date" -> date.toString)
      )
    else
      Option.when(date.isBefore(opened))(
        Rules.ActionDateBeforeIssue.refusal(
          "An action cannot be dated before the issue opened.",
          "action" -> id,
          "date"   -> date.toString,
          "issue"  -> issueId
        )
      )

  def checkForward(id: String, from: ActionState, to: ActionState): Option[Refusal] =
    Option.when(to.ordinal < from.ordinal)(
      Rules.ActionStateBackwards
        .refusal(s"An action that is ${from.label} cannot become ${to.label}.", "action" -> id)
    )
