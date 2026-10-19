package voice

import java.time.{Clock as JClock, Duration, Instant}
import java.util.Base64

/** A clock the edge reads once per request; handlers have none (R12). Tests set it. */
trait Clock:
  def now(): Instant

object Clock:
  val system: Clock = () => JClock.systemUTC().instant()

/**
 * What the service reads from its environment, read once at start and handed to what needs it.
 * Names only in this repository; the values are the installation's (contracts/deployment.md).
 */
final case class Settings(
    sessionKey: Array[Byte],
    sessionHours: Long,
    inviteCodes: Set[String],
    dormancyPeriod: Duration,
    suppressionThreshold: Int,
    reasoningService: String
)

object Settings:

  /** `180d`, `12h`, `30m`, `45s`, or a bare number of days. */
  def duration(text: String): Option[Duration] =
    val t              = text.trim
    val (number, unit) = t.span(_.isDigit)
    number.toLongOption.flatMap { n =>
      unit match
        case "" | "d" => Some(Duration.ofDays(n))
        case "h"      => Some(Duration.ofHours(n))
        case "m"      => Some(Duration.ofMinutes(n))
        case "s"      => Some(Duration.ofSeconds(n))
        case "ms"     => Some(Duration.ofMillis(n))
        case _        => None
    }

  def from(env: String => Option[String]): Settings =
    val key = env("VOICE_SESSION_KEY")
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(
        throw IllegalStateException("VOICE_SESSION_KEY is not set: the base64 of 32 random bytes")
      )
    val bytes = Base64.getDecoder.decode(key)
    if bytes.length < 32 then
      throw IllegalStateException("VOICE_SESSION_KEY must be at least 32 bytes")
    Settings(
      sessionKey = bytes,
      sessionHours = env("VOICE_SESSION_HOURS").flatMap(_.trim.toLongOption).getOrElse(12L),
      inviteCodes = env("VOICE_INVITE_CODES").toSeq
        .flatMap(_.split(','))
        .map(_.trim)
        .filter(_.nonEmpty)
        .toSet,
      dormancyPeriod =
        env("VOICE_DORMANCY_PERIOD").flatMap(duration).getOrElse(Duration.ofDays(180)),
      suppressionThreshold =
        env("VOICE_SUPPRESSION_THRESHOLD").flatMap(_.trim.toIntOption).getOrElse(5),
      reasoningService =
        env("VOICE_REASONING_SERVICE").map(_.trim).filter(_.nonEmpty).getOrElse("reasoning")
    )

  def fromEnv(): Settings = from(sys.env.get)
