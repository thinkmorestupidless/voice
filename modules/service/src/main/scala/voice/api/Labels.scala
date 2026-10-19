package voice.api

import voice.domain.*

/**
 * The words a person sees for progress, from `StateLabels` only (FR-061). An interface renders
 * these and invents none of its own; the Issue and chronology routes carry the seven chronology
 * states once, in a header, so the interface has the server's vocabulary.
 */
object Labels:

  val HeaderName = "Voice-State-Labels"

  /** `submitted,ai-interpreted,...` in FR-042's order. */
  val header: (String, String) =
    HeaderName -> ChronologyState.values.map(s => slug(s.label)).mkString(",")

  def slug(label: String): String = label.toLowerCase.replace(' ', '-')

  def chronology(state: ChronologyState): String       = state.label
  def contribution(state: ContributionState): String   = state.label
  def investigation(state: InvestigationState): String = state.label
  def action(state: ActionState): String               = state.label

  /** An action with no recorded response stays visible as unanswered (FR-041). */
  def unanswered(state: ActionState, responded: Boolean): String =
    if responded || state == ActionState.Completed then state.label
    else s"${state.label}; no response recorded"
