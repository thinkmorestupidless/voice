package voice.domain.issues

import java.time.Instant

/**
 * One way an Issue could be addressed, described without endorsement (FR-032): what it costs, what
 * it gains and what is still unknown. No field ranks, scores or recommends it; a body that tries is
 * refused at the edge (`option.endorsement`).
 */
final case class PolicyOptionState(
    optionId: String,
    issueId: String,
    title: String,
    description: String,
    costs: Vector[String],
    benefits: Vector[String],
    openQuestions: Vector[String],
    updatedAt: Option[Instant]
):
  def exists: Boolean = optionId.nonEmpty

object PolicyOptionState:
  val empty: PolicyOptionState =
    PolicyOptionState("", "", "", "", Vector.empty, Vector.empty, Vector.empty, None)
