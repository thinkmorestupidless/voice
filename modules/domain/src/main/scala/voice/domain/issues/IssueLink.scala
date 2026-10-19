package voice.domain.issues

/**
 * The only record that joins a public copy to its author (R3): `pub-…` to the contribution and the
 * participant. Restricted; erased by overwriting with the empty link and deleting (FR-055).
 */
final case class IssueLinkState(
    contributionId: String,
    participantId: String,
    issueId: String,
    version: Int
):
  def exists: Boolean = contributionId.nonEmpty

object IssueLinkState:
  val empty: IssueLinkState = IssueLinkState("", "", "", 0)
