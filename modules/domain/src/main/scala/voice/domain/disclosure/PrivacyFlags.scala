package voice.domain.disclosure

import voice.domain.PrivacyFlag

/**
 * What a privacy check found in a derivative (FR-016, R6): the flags that require enhanced review,
 * the identifying details a participant should consider removing, and a reason for each, in words a
 * reviewer reads. Reasons quote a category, never the text that matched.
 */
final case class Detection(kind: String, from: Int, to: Int)

final case class PrivacyCheck(
    flags: Vector[PrivacyFlag],
    reasons: Vector[String],
    detections: Vector[Detection]
):
  def merge(other: PrivacyCheck): PrivacyCheck =
    PrivacyCheck(
      (flags ++ other.flags).distinct,
      (reasons ++ other.reasons).distinct,
      (detections ++ other.detections).distinct
    )

object PrivacyCheck:
  val clear: PrivacyCheck = PrivacyCheck(Vector.empty, Vector.empty, Vector.empty)
