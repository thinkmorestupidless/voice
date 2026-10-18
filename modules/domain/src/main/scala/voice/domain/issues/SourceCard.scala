package voice.domain.issues

import java.time.Instant

/**
 * What a reader is told about a source beside reasoning's record of it (seed, FR-021): its name,
 * licence, attribution and link. All public.
 */
final case class SourceCardState(
    sourceId: String,
    name: String,
    licence: String,
    attribution: String,
    link: String,
    publishedAt: Option[Instant]
):
  def exists: Boolean = sourceId.nonEmpty

object SourceCardState:
  val empty: SourceCardState = SourceCardState("", "", "", "", "", None)
