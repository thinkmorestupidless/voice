package voice.domain.accountability

import voice.domain.*

import java.time.Instant

/**
 * What an action achieved, as measured, with its limitations (FR-043). `claimsResolved` is false
 * unless the record says so: an outcome never implies the problem is solved.
 */
final case class OutcomeRecord(
    outcomeId: String,
    issueId: String,
    actionId: String,
    measured: String,
    limitations: String,
    claimsResolved: Boolean,
    recordedBy: String,
    recordedAt: Instant,
    developmentId: String
):
  def exists: Boolean = outcomeId.nonEmpty

object OutcomeRecord:
  val empty: OutcomeRecord = OutcomeRecord("", "", "", "", "", false, "", Instant.EPOCH, "")

enum OutcomeEvent:
  case OutcomeRecorded(record: OutcomeRecord, provenance: Provenance)

object Outcome:
  def checkAction(issueId: String, actions: Seq[String], actionId: String): Option[Refusal] =
    Option.unless(actions.contains(actionId))(
      Rules.OutcomeActionNotHeld
        .refusal("The issue holds no such action.", "issue" -> issueId, "action" -> actionId)
    )
