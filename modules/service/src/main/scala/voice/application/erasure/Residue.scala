package voice.application.erasure

import voice.domain.disclosure.ContributionRecord
import voice.domain.erasure.*

/**
 * Whether a published de-identified copy may stay once its author is erased (FR-057). It stays only
 * when it carries nothing a reviewer or the privacy check flagged: a flag for a distinctive
 * circumstance, health, a minor, a small group or anything else possibly identifying is a reason
 * the text could point back to its author, and the copy is withdrawn with the rest. What stays has
 * no link to the author (the issue link is erased) and is counted by cause, never by person.
 */
object Residue:
  def decide(record: ContributionRecord): ResidueResult =
    if record.flagged then ResidueResult.Suppressed(ResidueReason.DistinctiveCombination)
    else ResidueResult.Retained
