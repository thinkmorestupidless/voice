package voice.domain.issues

import java.time.Instant

/** One public text a search row is made of, under the public reference it came from. */
final case class SearchPart(ref: String, text: String)

/**
 * One row of the `public-search` view (R14): a public record's kind, id, title, public text,
 * version or date, and the link to read it. Every field is public by classification; a test holds
 * the view to it (`public.search.filter`).
 */
final case class SearchRow(
    kind: String,
    recordId: String,
    title: String,
    text: String,
    parts: Vector[SearchPart],
    version: Option[String],
    date: Option[Instant],
    link: String
)
