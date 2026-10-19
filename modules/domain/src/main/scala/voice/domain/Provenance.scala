package voice.domain

import java.time.Instant

/**
 * Who or what caused a transition.
 *
 * `Participant` names the participant and is personal; it never appears on a public record. A
 * participant acting on a public record (an Issue) is recorded as `Citizen`, which names no one.
 */
enum Actor:
  case Participant(id: String)
  case Citizen
  case Staff(subject: String)
  case System(component: String)

  /** This actor as a public record may carry it: a participant becomes `Citizen`. */
  def forPublic: Actor = this match
    case Participant(_) => Citizen
    case other          => other

  def render: String = this match
    case Participant(_)    => "participant"
    case Citizen           => "a participant"
    case Staff(subject)    => subject
    case System(component) => s"system:$component"

/**
 * Where a transition came from: the actor, the time the edge read once, what caused it, and the
 * request or chain it belongs to (FR-062, R12). Set at the edge, passed on in command metadata and
 * recorded on every event.
 */
final case class Provenance(actor: Actor, at: Instant, causationId: String, correlationId: String):

  /** The same provenance for a command this one caused. */
  def causing(actor: Actor, causationId: String): Provenance =
    copy(actor = actor, causationId = causationId)

  def forPublic: Provenance = copy(actor = actor.forPublic)

object Provenance:

  /** Metadata keys a provenance travels under between components. */
  object Keys:
    val Actor       = "voice-actor"
    val At          = "voice-at"
    val Causation   = "voice-causation"
    val Correlation = "voice-correlation"

  def encodeActor(actor: Actor): String = actor match
    case Actor.Participant(id)   => s"participant:$id"
    case Actor.Citizen           => "citizen"
    case Actor.Staff(subject)    => s"staff:$subject"
    case Actor.System(component) => s"system:$component"

  def decodeActor(text: String): Option[Actor] = text.split(":", 2) match
    case Array("participant", id)   => Some(Actor.Participant(id))
    case Array("citizen")           => Some(Actor.Citizen)
    case Array("staff", subject)    => Some(Actor.Staff(subject))
    case Array("system", component) => Some(Actor.System(component))
    case _                          => None

  /** As metadata pairs. */
  def toPairs(p: Provenance): Seq[(String, String)] = Seq(
    Keys.Actor       -> encodeActor(p.actor),
    Keys.At          -> p.at.toString,
    Keys.Causation   -> p.causationId,
    Keys.Correlation -> p.correlationId
  )

  /** From metadata, when all four keys are there. */
  def fromLookup(get: String => Option[String]): Option[Provenance] =
    for
      actor       <- get(Keys.Actor).flatMap(decodeActor)
      at          <- get(Keys.At).flatMap(t => scala.util.Try(Instant.parse(t)).toOption)
      causation   <- get(Keys.Causation)
      correlation <- get(Keys.Correlation)
    yield Provenance(actor, at, causation, correlation)
