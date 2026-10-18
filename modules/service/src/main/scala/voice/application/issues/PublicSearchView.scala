package voice.application.issues

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.issues.*
import voice.domain.issues.IssueEvent.*

/**
 * Keyword search for anyone (R14, FR-026, FR-027): one row per public record, from the Issues, the
 * source cards and, from US4, the policy options. Every column is public by classification
 * (`public.search.filter`), so access is filtered before anything is searched: what is not public
 * was never written here. A de-identified copy is searchable; an attributed or restricted one is
 * sealed and never is.
 */
final class PublicSearchView extends KeyedView[SearchRow]:

  private def issueRow(subject: String): SearchRow =
    SearchRow("issue", subject, "", "", Vector.empty, None, None, s"/public/issues/$subject")

  private def text(r: SearchRow): SearchRow =
    r.copy(text =
      (Seq(r.title, r.parts.find(_.ref == "summary").map(_.text).getOrElse("")) ++ r.parts
        .filter(_.ref != "summary")
        .map(_.text)).filter(_.nonEmpty).mkString("\n")
    )

  def onIssue(event: IssueEvent, change: Change): Effect =
    val key = s"issue:${change.subject}"
    val row = change.rows.get(key).getOrElse(issueRow(change.subject))
    def put(f: SearchRow => SearchRow, at: java.time.Instant) =
      effects.updateRow(key, text(f(row)).copy(date = Some(at), version = Some(at.toString)))
    event match
      case IssueOpened(title, summary, p) =>
        put(_.copy(title = title, parts = Vector(SearchPart("summary", summary))), p.at)
      case ContributionPublished(pub, t, p) =>
        put(r => r.copy(parts = r.parts :+ SearchPart(pub, t)), p.at)
      case ContributionWithdrawn(pub, p) =>
        put(r => r.copy(parts = r.parts.filterNot(_.ref == pub)), p.at)
      case _ => effects.ignore()

  def onSource(state: SourceCardState, change: Change): Effect =
    if !state.exists then effects.ignore()
    else
      effects.updateRow(
        s"source:${change.subject}",
        SearchRow(
          "source",
          state.sourceId,
          state.name,
          Seq(state.name, state.attribution).mkString("\n"),
          Vector.empty,
          state.publishedAt.map(_.toString),
          state.publishedAt,
          s"/public/sources/${state.sourceId}"
        )
      )

  def onOption(state: PolicyOptionState, change: Change): Effect =
    if !state.exists then effects.ignore()
    else
      effects.updateRow(
        s"option:${change.subject}",
        SearchRow(
          "option",
          state.optionId,
          state.title,
          (Seq(
            state.title,
            state.description
          ) ++ state.costs ++ state.benefits ++ state.openQuestions).mkString("\n"),
          Vector.empty,
          state.updatedAt.map(_.toString),
          state.updatedAt,
          s"/public/issues/${state.issueId}#${state.optionId}"
        )
      )

object PublicSearch
    extends KeyedView.Companion[PublicSearchView, SearchRow](
      ComponentId("public-search"),
      Codecs.serializer[SearchRow]("public-search-row")
    ):
  val issues  = source(ChangeSource.eventsOf(IssueEntity))(_.onIssue)
  val sources = source(ChangeSource.stateOf(SourceCardEntity))(_.onSource)
  val options = source(ChangeSource.stateOf(PolicyOptionEntity))(_.onOption)

  /**
   * The columns the query reads; the build-time test holds them to public fields of `SearchRow`.
   */
  val Columns: Seq[String] = Seq("text", "date")

  /** The query's text, kept apart so the build-time test can read the columns it names. */
  val SearchSql: String =
    s"SELECT payload FROM $table WHERE to_tsvector('english', payload::jsonb->>'text') @@ plainto_tsquery('english', :q) ORDER BY payload::jsonb->>'date' DESC NULLS LAST"

  val search = query("search")(SearchSql)

  def create(ctx: ViewComponentContext) = new PublicSearchView
