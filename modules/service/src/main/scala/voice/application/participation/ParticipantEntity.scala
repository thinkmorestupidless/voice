package voice.application.participation

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.participation.*
import voice.domain.participation.ParticipantEvent.*

final case class CreateParticipant(recoveryHash: String, provenance: Provenance)
final case class StartSession(provenance: Provenance)
final case class RecoverSession(presentedHash: String, newHash: String, provenance: Provenance)
final case class CheckCredential(presentedHash: String)
final case class MarkErasure(cause: ErasureCause, provenance: Provenance)

/**
 * A participant: created at session start, its sessions recorded (the dormancy clock reads the
 * last, FR-063), recovered by the credential whose hash it holds, and erased. Once erased it
 * refuses every command but a query (`participant.erased`).
 */
final class ParticipantEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[ParticipantState, ParticipantEvent]:

  private def id = context.entityId

  def emptyState: ParticipantState = ParticipantState.empty

  def applyEvent(event: ParticipantEvent): ParticipantState = Participant(currentState, event)

  private def erased[R]: Effect[R] =
    val (m, c) = Refuse(
      Rules.ParticipantErased
        .refusal("This participant's data has been erased.", "participant" -> id)
    )
    effects.error(m, c)

  def create(command: CreateParticipant): Effect[Done] =
    if currentState.created then effects.reply(Done)
    else
      effects
        .persist(ParticipantCreated(command.recoveryHash, command.provenance))
        .thenReply(_ => Done)

  def startSession(command: StartSession): Effect[Done] =
    if currentState.erased then erased
    else if !currentState.created then
      val (m, c) = Refuse(Rules.SessionRequired.refusal("There is no such participant."))
      effects.error(m, c)
    else effects.persist(SessionStarted(command.provenance)).thenReply(_ => Done)

  def recover(command: RecoverSession): Effect[Done] =
    if currentState.erased then erased
    else if !currentState.created || currentState.recoveryHash != command.presentedHash then
      val (m, c) = Refuse(
        Rules.ParticipantCredentialInvalid.refusal("That recovery credential does not match.")
      )
      effects.error(m, c)
    else effects.persist(SessionRecovered(command.newHash, command.provenance)).thenReply(_ => Done)

  def requestErasure(command: MarkErasure): Effect[Participant.Summary] =
    currentState.erasure match
      case ParticipantErasure.None =>
        effects
          .persist(ErasureRequested(command.cause, command.provenance))
          .thenReply(s => Participant.summary(s))
      case _ => effects.reply(Participant.summary(currentState))

  def completeErasure(command: MarkErasure): Effect[Participant.Summary] =
    if currentState.erased then effects.reply(Participant.summary(currentState))
    else
      effects
        .persist(ErasureCompleted(command.cause, command.provenance))
        .thenReply(s => Participant.summary(s))

  /** Whether `presentedHash` is the credential this participant holds; changes nothing (FR-053). */
  def checkCredential(command: CheckCredential): Effect[Boolean] =
    effects.reply(
      currentState.created && !currentState.erased && currentState.recoveryHash == command.presentedHash
    )

  def summary: ReadOnlyEffect[Participant.Summary] =
    effects.reply(Participant.summary(currentState))

object ParticipantEntity
    extends EventSourcedEntity.Companion[ParticipantEntity, ParticipantState, ParticipantEvent](
      componentId = ComponentId("participant"),
      stateSerializer = Codecs.serializer[ParticipantState]("participant"),
      eventSerializer = Codecs.serializer[ParticipantEvent]("participant-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[CreateParticipant] = Codecs.serializer[CreateParticipant]("participant-create")
  given Serializer[StartSession]      = Codecs.serializer[StartSession]("participant-start-session")
  given Serializer[RecoverSession]    = Codecs.serializer[RecoverSession]("participant-recover")
  given Serializer[MarkErasure]       = Codecs.serializer[MarkErasure]("participant-mark-erasure")
  given Serializer[CheckCredential] =
    Codecs.serializer[CheckCredential]("participant-check-credential")
  given Serializer[Participant.Summary] =
    Codecs.serializer[Participant.Summary]("participant-summary")

  def create(context: EventSourcedEntityContext) = new ParticipantEntity(context)

  val create          = command("create")(_.create)
  val startSession    = command("start-session")(_.startSession)
  val recover         = command("recover")(_.recover)
  val requestErasure  = command("request-erasure")(_.requestErasure)
  val completeErasure = command("complete-erasure")(_.completeErasure)
  val checkCredential = command("check-credential")(_.checkCredential)
  val summary         = query("summary")(_.summary)
