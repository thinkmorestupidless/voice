package voice.domain.accountability

import voice.domain.*

import java.time.Instant

/** A decision on an Issue: what was chosen, by what authority, over what, and why (FR-043). */
final case class DecisionRecord(
    decisionId: String,
    issueId: String,
    approach: String,
    authority: String,
    alternatives: Vector[String],
    justification: String,
    recordedBy: String,
    recordedAt: Instant,
    developmentId: String
):
  def exists: Boolean = decisionId.nonEmpty

object DecisionRecord:
  val empty: DecisionRecord =
    DecisionRecord("", "", "", "", Vector.empty, "", "", Instant.EPOCH, "")

enum DecisionEvent:
  case DecisionRecorded(record: DecisionRecord, provenance: Provenance)
