package voice.domain.participation

import voice.domain.*

import java.time.Instant

/** Who wrote a message in a conversation. */
enum Author:
  case Citizen, Voice

/** One message, sealed under the participant's key (FR-013). */
final case class Message(seq: Int, author: Author, text: Sealed[String], at: Instant)

/**
 * Where in a citizen's message an element of a proposal came from: characters `from` until `to`.
 */
final case class Span(message: Int, from: Int, to: Int)

/** The kinds of thing a concern is made of. */
enum ElementKind:
  case Problem, Who, Where, When, Impact, Wish

final case class Element(kind: ElementKind, text: Sealed[String], spans: Vector[Span])

enum Confidence:
  case Low, Medium, High

/** An Issue Voice suggests a concern may join, by a score; never a decision (FR-017). */
final case class SuggestedIssue(issueId: String, title: String, score: Double)

enum ProposalState:
  case Open
  case Confirmed(concernId: String)
  case Discarded

/**
 * Voice's interpretation of what a participant said: the original statement quoted from their own
 * words, a summary, the elements with the passages they came from, a confidence and the Issues it
 * may relate to (FR-007). Personal throughout: every text is sealed.
 */
final case class ConcernProposal(
    proposalId: String,
    fromMessages: Vector[Int],
    original: Sealed[String],
    summary: Sealed[String],
    elements: Vector[Element],
    confidence: Confidence,
    suggestedIssues: Vector[SuggestedIssue],
    editedByCitizen: Boolean,
    state: ProposalState
)

final case class ConversationState(
    participantId: String,
    started: Boolean,
    messages: Vector[Message],
    proposals: Vector[ConcernProposal],
    confirmedConcerns: Vector[String],
    repliedTurns: Vector[String] = Vector.empty
):
  def nextSeq: Int = messages.lastOption.map(_.seq + 1).getOrElse(1)
  def proposal(id: String): Option[ConcernProposal] = proposals.find(_.proposalId == id)

object ConversationState:
  val empty: ConversationState =
    ConversationState("", false, Vector.empty, Vector.empty, Vector.empty)

enum ConversationEvent:
  case ConversationStarted(participantId: String, provenance: Provenance)
  case MessageReceived(message: Message, provenance: Provenance)
  case ReplySent(message: Message, turnId: String, provenance: Provenance)
  case ProposalMade(proposal: ConcernProposal, provenance: Provenance)
  case ProposalEdited(
      proposalId: String,
      summary: Sealed[String],
      elements: Vector[Element],
      provenance: Provenance
  )
  case ProposalConfirmed(proposalId: String, concernId: String, provenance: Provenance)
  case ProposalDiscarded(proposalId: String, provenance: Provenance)

object Conversation:

  val MaxMessageLength = 4000

  def apply(state: ConversationState, event: ConversationEvent): ConversationState =
    import ConversationEvent.*
    def withProposal(id: String)(f: ConcernProposal => ConcernProposal) =
      state.copy(proposals = state.proposals.map(p => if p.proposalId == id then f(p) else p))
    event match
      case ConversationStarted(participant, _) =>
        state.copy(participantId = participant, started = true)
      case MessageReceived(m, _) => state.copy(messages = state.messages :+ m)
      case ReplySent(m, turn, _) =>
        state.copy(messages = state.messages :+ m, repliedTurns = state.repliedTurns :+ turn)
      case ProposalMade(p, _) => state.copy(proposals = state.proposals :+ p)
      case ProposalEdited(id, summary, elements, _) =>
        withProposal(id)(_.copy(summary = summary, elements = elements, editedByCitizen = true))
      case ProposalConfirmed(id, concern, _) =>
        withProposal(id)(_.copy(state = ProposalState.Confirmed(concern)))
          .copy(confirmedConcerns = state.confirmedConcerns :+ concern)
      case ProposalDiscarded(id, _) => withProposal(id)(_.copy(state = ProposalState.Discarded))

  /** The rules a citizen's command on a conversation is held to, before anything is recorded. */
  def checkOwner(
      state: ConversationState,
      conversationId: String,
      participantId: String
  ): Option[Refusal] =
    Option.when(!state.started || state.participantId != participantId)(
      Rules.ConversationNotOwned
        .refusal("This conversation is not yours.", "conversation" -> conversationId)
    )

  def checkMessage(conversationId: String, length: Int): Option[Refusal] =
    Option.when(length == 0 || length > MaxMessageLength)(
      Rules.ConversationMessageEmpty.refusal(
        s"A message has between 1 and $MaxMessageLength characters.",
        "conversation" -> conversationId
      )
    )

  def checkConfirmable(
      state: ConversationState,
      conversationId: String,
      proposalId: String
  ): Either[Refusal, ConcernProposal] =
    state.proposal(proposalId) match
      case None =>
        Left(
          Rules.ProposalNotHeld.refusal(
            "The conversation holds no such proposal.",
            "conversation" -> conversationId,
            "proposal"     -> proposalId
          )
        )
      case Some(p) =>
        p.state match
          case ProposalState.Confirmed(concern) =>
            Left(
              Rules.ProposalAlreadyConfirmed.refusal(
                "This proposal is already confirmed.",
                "proposal" -> proposalId,
                "concern"  -> concern
              )
            )
          case ProposalState.Discarded =>
            Left(
              Rules.ProposalNotHeld.refusal(
                "This proposal was discarded.",
                "conversation" -> conversationId,
                "proposal"     -> proposalId
              )
            )
          case ProposalState.Open => Right(p)
