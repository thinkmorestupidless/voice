package voice.application.participation

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.participation.*
import voice.domain.participation.ConversationEvent.*

final case class StartConversation(participantId: String, provenance: Provenance)
final case class ReceiveMessage(
    participantId: String,
    text: Sealed[String],
    length: Int,
    provenance: Provenance
)
final case class RecordReply(
    turnId: String,
    text: Sealed[String],
    proposal: Option[ConcernProposal],
    provenance: Provenance
)
final case class EditProposal(
    participantId: String,
    proposalId: String,
    summary: Sealed[String],
    elements: Vector[Element],
    provenance: Provenance
)
final case class ConfirmProposal(
    participantId: String,
    proposalId: String,
    concernId: String,
    provenance: Provenance
)
final case class DiscardProposal(participantId: String, proposalId: String, provenance: Provenance)
final case class OwnedBy(participantId: String)

/**
 * A private conversation (R5): its messages and proposals, sealed. The citizen's commands carry the
 * participant the session named, and the conversation refuses any other (`conversation.not-owned`).
 * A proposal is confirmed by the citizen's command only (FR-008, FR-009) and keeps the messages it
 * came from; a later message does not change it (edge case 1).
 */
final class ConversationEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[ConversationState, ConversationEvent]:

  private def id = context.entityId

  def emptyState: ConversationState = ConversationState.empty

  def applyEvent(event: ConversationEvent): ConversationState = Conversation(currentState, event)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def start(command: StartConversation): Effect[ConversationState] =
    if currentState.started then
      Conversation
        .checkOwner(currentState, id, command.participantId)
        .fold(effects.reply(currentState))(refuse)
    else
      effects
        .persist(ConversationStarted(command.participantId, command.provenance))
        .thenReply(s => s)

  def receive(command: ReceiveMessage): Effect[Message] =
    Conversation
      .checkOwner(currentState, id, command.participantId)
      .orElse(Conversation.checkMessage(id, command.length)) match
      case Some(r) => refuse(r)
      case None =>
        val message =
          Message(currentState.nextSeq, Author.Citizen, command.text, command.provenance.at)
        effects.persist(MessageReceived(message, command.provenance)).thenReply(_ => message)

  /** Voice's reply to a turn, once: a retried step finds the turn answered and records nothing. */
  def reply(command: RecordReply): Effect[Message] =
    if currentState.repliedTurns.contains(command.turnId) then
      effects.reply(currentState.messages.filter(_.author == Author.Voice).last)
    else
      val message = Message(currentState.nextSeq, Author.Voice, command.text, command.provenance.at)
      val events = Seq(ReplySent(message, command.turnId, command.provenance)) ++
        command.proposal
          .filterNot(p => currentState.proposal(p.proposalId).isDefined)
          .map(ProposalMade(_, command.provenance))
      effects.persistAll(events).thenReply(_ => message)

  def edit(command: EditProposal): Effect[ConcernProposal] =
    Conversation.checkOwner(currentState, id, command.participantId) match
      case Some(r) => refuse(r)
      case None =>
        Conversation.checkConfirmable(currentState, id, command.proposalId) match
          case Left(r) => refuse(r)
          case Right(_) =>
            effects
              .persist(
                ProposalEdited(
                  command.proposalId,
                  command.summary,
                  command.elements,
                  command.provenance
                )
              )
              .thenReply(s => s.proposal(command.proposalId).get)

  def confirm(command: ConfirmProposal): Effect[ConcernProposal] =
    Conversation.checkOwner(currentState, id, command.participantId) match
      case Some(r) => refuse(r)
      case None =>
        Conversation.checkConfirmable(currentState, id, command.proposalId) match
          case Left(r) => refuse(r)
          case Right(_) =>
            effects
              .persist(ProposalConfirmed(command.proposalId, command.concernId, command.provenance))
              .thenReply(s => s.proposal(command.proposalId).get)

  def discard(command: DiscardProposal): Effect[ConversationState] =
    Conversation.checkOwner(currentState, id, command.participantId) match
      case Some(r) => refuse(r)
      case None =>
        Conversation.checkConfirmable(currentState, id, command.proposalId) match
          case Left(r) => refuse(r)
          case Right(_) =>
            effects
              .persist(ProposalDiscarded(command.proposalId, command.provenance))
              .thenReply(s => s)

  /** Removes the conversation for good: the erasure workflow's purge (FR-055). */
  def delete: Effect[Done] =
    if !currentState.started then effects.reply(Done)
    else effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[ConversationState] = effects.reply(currentState)

object ConversationEntity
    extends EventSourcedEntity.Companion[ConversationEntity, ConversationState, ConversationEvent](
      componentId = ComponentId("conversation"),
      stateSerializer = Codecs.serializer[ConversationState]("conversation"),
      eventSerializer = Codecs.serializer[ConversationEvent]("conversation-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[StartConversation] = Codecs.serializer[StartConversation]("conversation-start")
  given Serializer[ReceiveMessage]    = Codecs.serializer[ReceiveMessage]("conversation-receive")
  given Serializer[RecordReply]       = Codecs.serializer[RecordReply]("conversation-reply")
  given Serializer[EditProposal]      = Codecs.serializer[EditProposal]("conversation-edit")
  given Serializer[ConfirmProposal]   = Codecs.serializer[ConfirmProposal]("conversation-confirm")
  given Serializer[DiscardProposal]   = Codecs.serializer[DiscardProposal]("conversation-discard")
  given Serializer[Message]           = Codecs.serializer[Message]("conversation-message")
  given Serializer[ConcernProposal]   = Codecs.serializer[ConcernProposal]("conversation-proposal")

  def create(context: EventSourcedEntityContext) = new ConversationEntity(context)

  val start   = command("start")(_.start)
  val receive = command("receive")(_.receive)
  val reply   = command("reply")(_.reply)
  val edit    = command("edit")(_.edit)
  val confirm = command("confirm")(_.confirm)
  val discard = command("discard")(_.discard)
  val delete  = command("delete")(_.delete)
  val get     = query("get")(_.get)

/** One row per conversation, for a participant's own listing: links and counts, no text. */
final case class ConversationRow(
    conversationId: String,
    participantId: String,
    messages: Int,
    proposals: Int,
    lastAt: Option[java.time.Instant]
)

final class ConversationRowsView extends View[ConversationEvent, ConversationRow]:
  def onChange(event: ConversationEvent): Effect =
    val row = rowState.getOrElse(ConversationRow(updateContext.subject, "", 0, 0, None))
    event match
      case ConversationStarted(participant, p) =>
        effects.updateRow(row.copy(participantId = participant, lastAt = Some(p.at)))
      case MessageReceived(m, _) =>
        effects.updateRow(row.copy(messages = row.messages + 1, lastAt = Some(m.at)))
      case ReplySent(m, _, _) =>
        effects.updateRow(row.copy(messages = row.messages + 1, lastAt = Some(m.at)))
      case ProposalMade(_, _) => effects.updateRow(row.copy(proposals = row.proposals + 1))
      case _                  => effects.ignore()

object ConversationRows
    extends View.Companion[ConversationRowsView, ConversationEvent, ConversationRow](
      componentId = ComponentId("conversation-rows"),
      source = ChangeSource.eventsOf(ConversationEntity),
      rowSerializer = Codecs.serializer[ConversationRow]("conversation-row")
    ):
  val ofParticipant = query("of-participant")(
    s"SELECT payload FROM $table WHERE payload::jsonb->>'participantId' = :participant"
  )
  def create(ctx: ViewComponentContext) = new ConversationRowsView
