package voice.domain.issues

/**
 * References to records the reasoning service holds (R4). Voice keeps the id, typed by what it
 * names, and reads the record each time it is shown; it never copies one.
 */
final case class QuestionRef(id: String)
final case class HypothesisRef(id: String):
  /** `<question>/<hypothesis>`, as reasoning names it. */
  def question: String = id.takeWhile(_ != '/')
final case class SourceRef(id: String)
final case class EvidenceRef(id: String)
final case class ClaimRef(id: String)
final case class BeliefRevisionRef(claim: ClaimRef, revisedBy: ClaimRef)

/** The ids Voice chooses in reasoning (contracts/reasoning-integration.md, "Identifiers"). */
object ReasoningIds:
  private val Valid = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}".r

  def question(issue: String, key: String): String      = s"voice.$issue.$key"
  def claim(issue: String, key: String): String         = s"voice.$issue.$key"
  def hypothesis(question: String, key: String): String = s"$question/$key"

  def valid(id: String): Boolean = Valid.matches(id)
