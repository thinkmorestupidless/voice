package voice.application.interests

import voice.domain.accountability.*
import voice.domain.issues.*

/** What a development's record says, read authoritatively when the update is prepared. */
enum Facts:
  case Action(record: ActionRecord)
  case Evidence(link: EvidenceLink)
  case Decision(record: DecisionRecord)
  case Outcome(record: OutcomeRecord, action: Option[ActionRecord])
  case Update(update: NarrativeUpdate)

/** An update's words, the public records it is about, and why it reached this follower. */
final case class Rendered(text: String, links: Vector[String], why: String)

/**
 * The wording of every update, built from the record and nothing else (FR-047): no model, no
 * reviewer, and no word stronger than the record. An action's state is its label when the update is
 * prepared; an outcome says the problem is resolved only when its record says so (FR-049, FR-061).
 */
object Renderer:

  val NotSolved = "The record does not say the problem is solved."

  def issueLink(issue: String): String = s"/public/issues/$issue"

  def render(issue: String, title: String, facts: Facts): Rendered =
    val page = issueLink(issue)
    facts match
      case Facts.Action(a) =>
        Rendered(
          s"A representative recorded an action on “$title”: ${a.publicSummary} " +
            s"Its state is “${a.stage.label}”. Owner: ${a.owner}. Authority: ${a.authority}. Dated ${a.date}.",
          Vector(s"$page#${a.actionId}"),
          why(title, "an action recorded on it")
        )
      case Facts.Evidence(l) =>
        Rendered(
          s"A reviewer accepted evidence on “$title”. Its scope: ${l.scope} Its limitations: ${l.limitations}",
          Vector(s"$page/evidence/${l.linkId}"),
          why(title, "evidence a reviewer accepted on it")
        )
      case Facts.Decision(d) =>
        Rendered(
          s"A representative recorded a decision on “$title”: ${d.approach} Authority: ${d.authority}. Justification: ${d.justification}",
          Vector(s"$page#${d.decisionId}"),
          why(title, "a decision recorded on it")
        )
      case Facts.Outcome(o, action) =>
        val about = action.fold("an action")(a => s"the action “${a.publicSummary}”")
        val resolution =
          if o.claimsResolved then "The outcome record states that the problem is resolved."
          else NotSolved
        Rendered(
          s"A representative recorded what $about achieved on “$title”: ${o.measured} Limitations: ${o.limitations} $resolution",
          Vector(s"$page#${o.outcomeId}", s"$page#${o.actionId}"),
          why(title, "an outcome recorded on it")
        )
      case Facts.Update(u) =>
        Rendered(
          s"A representative published an update on “$title”: “${u.text}”",
          Vector(s"$page#${u.developmentId}"),
          why(title, "an update a representative published on it")
        )

  private def why(title: String, what: String): String =
    s"You follow “$title”, and this is about $what. You chose inbox delivery; you can stop following at any time."
