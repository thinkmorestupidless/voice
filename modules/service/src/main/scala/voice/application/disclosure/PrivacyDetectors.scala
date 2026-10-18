package voice.application.disclosure

import voice.domain.PrivacyFlag
import voice.domain.disclosure.{Detection, PrivacyCheck}

import scala.io.Source
import scala.util.matching.Regex

/**
 * The deterministic half of the privacy check (FR-016, R6): patterns read from
 * `privacy/patterns.txt`, run before the model so a known category is never left to its judgement.
 * Reasons name the category only; they never quote what matched.
 */
object PrivacyDetectors:

  final case class Pattern(kind: String, regex: Regex)

  lazy val patterns: Vector[Pattern] =
    val stream = getClass.getResourceAsStream("/privacy/patterns.txt")
    try
      Source
        .fromInputStream(stream, "UTF-8")
        .getLines()
        .map(_.trim)
        .filter(l => l.nonEmpty && !l.startsWith("#"))
        .map { l =>
          val tab = l.indexOf('\t')
          Pattern(l.take(tab), l.drop(tab + 1).r)
        }
        .toVector
    finally stream.close()

  def flagOf(kind: String): PrivacyFlag = kind match
    case "health"      => PrivacyFlag.Health
    case "minor"       => PrivacyFlag.Minor
    case "small-group" => PrivacyFlag.SmallGroup
    case _             => PrivacyFlag.Distinctive

  private val reasons: Map[String, String] = Map(
    "email"       -> "it contains what looks like an email address",
    "phone"       -> "it contains what looks like a phone number",
    "address"     -> "it contains what looks like a street address",
    "postcode"    -> "it contains what looks like a postcode",
    "name"        -> "it contains what looks like a person's name",
    "health"      -> "it mentions a health detail",
    "minor"       -> "it mentions a child",
    "small-group" -> "it describes a very small group",
    "distinctive" -> "it describes a distinctive circumstance someone could be recognised by"
  )

  /**
   * The identifying kinds a participant is shown, so they can remove the detail before approving.
   */
  val identifying: Set[String] = Set("email", "phone", "address", "postcode", "name")

  def check(text: String): PrivacyCheck =
    val found = patterns.flatMap(p =>
      p.regex.findAllMatchIn(text).map(m => Detection(p.kind, m.start, m.end))
    )
    val kinds = found.map(_.kind).distinct
    PrivacyCheck(kinds.map(flagOf).distinct, kinds.map(reasons), found.sortBy(_.from))
