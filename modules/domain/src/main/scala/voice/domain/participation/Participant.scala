package voice.domain.participation

import voice.domain.*

import java.time.Instant

/**
 * A pseudonymous participant (FR-001, FR-002). No name, no email, no verified identity: only the
 * hash of the recovery credential they hold, and when they were last here (FR-063). The invite code
 * is checked at the edge and never stored.
 */
final case class ParticipantState(
    created: Boolean,
    recoveryHash: String,
    createdAt: Option[Instant],
    lastSessionAt: Option[Instant],
    erasure: ParticipantErasure
):
  def erased: Boolean = erasure match
    case ParticipantErasure.Completed(_, _) => true
    case _                                  => false

object ParticipantState:
  val empty: ParticipantState = ParticipantState(false, "", None, None, ParticipantErasure.None)

enum ParticipantErasure:
  case None
  case Requested(at: Instant, cause: ErasureCause)
  case Completed(at: Instant, cause: ErasureCause)

enum ParticipantEvent:
  case ParticipantCreated(recoveryHash: String, provenance: Provenance)
  case SessionStarted(provenance: Provenance)
  case SessionRecovered(recoveryHash: String, provenance: Provenance)
  case ErasureRequested(cause: ErasureCause, provenance: Provenance)
  case ErasureCompleted(cause: ErasureCause, provenance: Provenance)

object Participant:

  def apply(state: ParticipantState, event: ParticipantEvent): ParticipantState = event match
    case ParticipantEvent.ParticipantCreated(hash, p) =>
      state.copy(
        created = true,
        recoveryHash = hash,
        createdAt = Some(p.at),
        lastSessionAt = Some(p.at)
      )
    case ParticipantEvent.SessionStarted(p) => state.copy(lastSessionAt = Some(p.at))
    case ParticipantEvent.SessionRecovered(hash, p) =>
      state.copy(recoveryHash = hash, lastSessionAt = Some(p.at))
    case ParticipantEvent.ErasureRequested(cause, p) =>
      state.copy(erasure = ParticipantErasure.Requested(p.at, cause))
    case ParticipantEvent.ErasureCompleted(cause, p) =>
      // The hash goes too: nothing can recover an erased participant.
      state.copy(recoveryHash = "", erasure = ParticipantErasure.Completed(p.at, cause))

  /** What a participant answers about itself, with nothing personal in it. */
  final case class Summary(
      exists: Boolean,
      createdAt: Option[Instant],
      lastSessionAt: Option[Instant],
      erasure: String,
      cause: Option[ErasureCause]
  )

  def summary(state: ParticipantState): Summary =
    val (erasure, cause) = state.erasure match
      case ParticipantErasure.None            => ("none", scala.None)
      case ParticipantErasure.Requested(_, c) => ("requested", Some(c))
      case ParticipantErasure.Completed(_, c) => ("completed", Some(c))
    Summary(state.created, state.createdAt, state.lastSessionAt, erasure, cause)
