package voice.domain.participation

import voice.domain.*

/**
 * A concern the participant confirmed: their meaning, as they edited it, confirmed by them and
 * never by Voice (FR-008, FR-009). A statement by one person, never an assertion about the world
 * (FR-022).
 */
final case class ConcernState(
    participantId: String,
    conversationId: String,
    proposalId: String,
    summary: Option[Sealed[String]],
    elements: Vector[Element],
    confirmedBy: Option[Actor],
    contributions: Vector[String],
    confirmedAt: Option[java.time.Instant]
):
  def confirmed: Boolean = confirmedBy.isDefined

object ConcernState:
  val empty: ConcernState = ConcernState("", "", "", None, Vector.empty, None, Vector.empty, None)

enum ConcernEvent:
  case ConcernConfirmed(
      participantId: String,
      conversationId: String,
      proposalId: String,
      summary: Sealed[String],
      elements: Vector[Element],
      provenance: Provenance
  )
  case ContributionOpened(contributionId: String, provenance: Provenance)

object Concern:
  def apply(state: ConcernState, event: ConcernEvent): ConcernState = event match
    case ConcernEvent.ConcernConfirmed(participant, conversation, proposal, summary, elements, p) =>
      state.copy(
        participantId = participant,
        conversationId = conversation,
        proposalId = proposal,
        summary = Some(summary),
        elements = elements,
        confirmedBy = Some(p.actor),
        confirmedAt = Some(p.at)
      )
    case ConcernEvent.ContributionOpened(id, _) =>
      state.copy(contributions = state.contributions :+ id)
