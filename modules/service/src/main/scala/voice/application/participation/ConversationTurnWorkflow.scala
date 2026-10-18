package voice.application.participation

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.forAgent
import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.agents.{*, given}
import voice.api.Problems
import voice.domain.*
import voice.domain.participation.*
import voice.sealing.Sealing

import scala.concurrent.duration.DurationInt

enum TurnStatus:
  case NotStarted, Thinking, Replied, CouldNotInterpret

/**
 * One turn of a conversation: the model's work, journalled (R5). Its state holds no plaintext: the
 * model's answer is kept sealed under the participant's key between the two steps, and the reply
 * itself is recorded on the conversation.
 */
final case class TurnState(
    conversationId: String,
    seq: Int,
    participantId: String,
    status: TurnStatus,
    answer: Option[Sealed[Turn]],
    proposalId: Option[String],
    provenance: Option[Provenance]
)

final case class TurnStart(
    conversationId: String,
    seq: Int,
    participantId: String,
    provenance: Provenance
)

object Transcripts:
  given JsonValueCodec[String] = Problems.codec[String]

  /** The conversation as the model is shown it: `[n] citizen: …`, one message a line. */
  def render(sealing: Sealing, state: ConversationState): String =
    val texts = sealing.openAll(state.participantId, state.messages.map(_.text), Purposes.Message)
    state.messages
      .zip(texts)
      .map { (m, t) =>
        val who = if m.author == Author.Citizen then "citizen" else "voice"
        s"[${m.seq}] $who: ${t.render(identity).replace('\n', ' ')}"
      }
      .mkString("\n")

/** The purposes sealed values are bound to (the associated data of R1), one per field. */
object Purposes:
  val Message          = "conversation.message"
  val ProposalOriginal = "proposal.original"
  val ProposalSummary  = "proposal.summary"
  val ProposalElement  = "proposal.element"
  val ConcernSummary   = "concern.summary"
  val ConcernElement   = "concern.element"
  val Turn             = "turn.answer"

object Proposals:
  given JsonValueCodec[String] = Problems.codec[String]

  /**
   * The model's draft as a proposal, sealed. A span that does not point into one of the citizen's
   * own messages is dropped, and so is an element left with none, so every passage shown is real.
   */
  def fromDraft(
      sealing: Sealing,
      subject: String,
      state: ConversationState,
      proposalId: String,
      draft: ConcernProposalDraft,
      citizenTexts: Map[Int, String]
  ): ConcernProposal =
    def valid(s: DraftSpan) =
      citizenTexts.get(s.message).exists(t => s.from >= 0 && s.to > s.from && s.to <= t.length)
    val elements = draft.elements.flatMap { e =>
      val kind  = ElementKind.values.find(_.toString.equalsIgnoreCase(e.kind.trim))
      val spans = e.spans.filter(valid).map(s => Span(s.message, s.from, s.to))
      kind
        .filter(_ => spans.nonEmpty)
        .map(k => Element(k, sealing.seal(subject, Purposes.ProposalElement, e.text), spans))
    }
    val from = elements.flatMap(_.spans.map(_.message)).distinct.sorted match
      case Vector() => state.messages.filter(_.author == Author.Citizen).map(_.seq).takeRight(1)
      case seqs     => seqs
    ConcernProposal(
      proposalId = proposalId,
      fromMessages = from,
      original = sealing.seal(subject, Purposes.ProposalOriginal, draft.original),
      summary = sealing.seal(subject, Purposes.ProposalSummary, draft.summary),
      elements = elements,
      confidence = Confidence.values
        .find(_.toString.equalsIgnoreCase(draft.confidence.trim))
        .getOrElse(Confidence.Medium),
      suggestedIssues = Vector.empty,
      editedByCitizen = false,
      state = ProposalState.Open
    )

final class ConversationTurnWorkflow(context: WorkflowContext) extends Workflow[TurnState]:
  import ConversationTurnWorkflow.*

  def emptyState: TurnState = TurnState("", 0, "", TurnStatus.NotStarted, None, None, None)

  private def conversation =
    context.componentClient.forEventSourcedEntity(EntityId(currentState.conversationId))

  def start(command: TurnStart): Effect[TurnStatus] =
    if currentState.status != TurnStatus.NotStarted then effects.reply(currentState.status)
    else
      effects
        .updateState(
          TurnState(
            command.conversationId,
            command.seq,
            command.participantId,
            TurnStatus.Thinking,
            None,
            None,
            Some(command.provenance)
          )
        )
        .transitionTo(ConversationTurnWorkflow.extract.ref)
        .thenReply(TurnStatus.Thinking)

  /** Calls the model once; its answer is sealed into the state before anything uses it. */
  def extractStep: StepEffect =
    val sealing    = Sealing(context.secrets)
    val held       = conversation.call(ConversationEntity.get).invoke()
    val transcript = Transcripts.render(sealing, held)
    val turn = context.componentClient
      .forAgent(SessionId(context.workflowId.replace(':', '-')))
      .call(ConcernExtractionAgent.turn)
      .invoke(TurnInput(transcript))
    val answer = sealing.seal[Turn](currentState.participantId, Purposes.Turn, turn)
    stepEffects
      .updateState(currentState.copy(answer = Some(answer)))
      .thenTransitionTo(ConversationTurnWorkflow.apply.ref)

  /** Records the reply, and the proposal if there is one, on the conversation, sealed. */
  def applyStep: StepEffect =
    val sealing = Sealing(context.secrets)
    val subject = currentState.participantId
    val turn = currentState.answer
      .map(sealing.open[Turn](_, Purposes.Turn))
      .flatMap(_.toOption)
      .getOrElse(throw IllegalStateException("the turn's answer cannot be opened"))
    val held = conversation.call(ConversationEntity.get).invoke()
    val citizenTexts = held.messages
      .filter(_.author == Author.Citizen)
      .map(m =>
        m.seq -> sealing.openText(m.text, Purposes.Message)(using
          Proposals.given_JsonValueCodec_String
        )
      )
      .toMap
    val at = currentState.provenance.map(_.at).getOrElse(java.time.Instant.EPOCH)
    val proposal = turn.proposal.map { draft =>
      Proposals.fromDraft(
        sealing,
        subject,
        held,
        Ids.mint(IdKind.Proposal, at),
        draft,
        citizenTexts
      )
    }
    val replyText =
      (turn.reply +: turn.clarifyingQuestion.toSeq).filter(_.nonEmpty).distinct.mkString(" ")
    conversation
      .call(ConversationEntity.reply)
      .invoke(
        RecordReply(
          context.workflowId,
          sealing.seal(subject, Purposes.Message, replyText)(using
            Proposals.given_JsonValueCodec_String
          ),
          proposal,
          provenance
        )
      ): Unit
    stepEffects
      .updateState(
        currentState.copy(status = TurnStatus.Replied, proposalId = proposal.map(_.proposalId))
      )
      .thenEnd

  /**
   * When the model could not be understood twice: say so to the citizen, never fail silently
   * (FR-006).
   */
  def couldNotInterpretStep: StepEffect =
    val sealing = Sealing(context.secrets)
    conversation
      .call(ConversationEntity.reply)
      .invoke(
        RecordReply(
          context.workflowId,
          sealing.seal(currentState.participantId, Purposes.Message, CouldNotInterpret)(using
            Proposals.given_JsonValueCodec_String
          ),
          None,
          provenance
        )
      ): Unit
    stepEffects.updateState(currentState.copy(status = TurnStatus.CouldNotInterpret)).thenEnd

  private def provenance: Provenance =
    currentState.provenance
      .getOrElse(
        Provenance(
          Actor.System("conversation-turn"),
          java.time.Instant.EPOCH,
          context.workflowId,
          context.workflowId
        )
      )
      .causing(Actor.System("conversation-turn"), context.workflowId)

  def status: ReadOnlyEffect[TurnState] = effects.reply(currentState)

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(30.seconds)
      .stepTimeout(ConversationTurnWorkflow.extract, 150.seconds)
      .stepRecovery(
        ConversationTurnWorkflow.extract,
        RecoverStrategy.maxRetries(1).failoverTo(ConversationTurnWorkflow.couldNotInterpret)
      )
      .stepRecovery(
        ConversationTurnWorkflow.apply,
        RecoverStrategy.maxRetries(2).failoverTo(ConversationTurnWorkflow.couldNotInterpret)
      )
      .build

object ConversationTurnWorkflow
    extends Workflow.Companion[ConversationTurnWorkflow, TurnState](
      componentId = ComponentId("conversation-turn"),
      stateSerializer = Codecs.serializer[TurnState]("conversation-turn")
    ):
  given Serializer[TurnStart]  = Codecs.serializer[TurnStart]("conversation-turn-start")
  given Serializer[TurnStatus] = Codecs.serializer[TurnStatus]("conversation-turn-status")

  val CouldNotInterpret = "Voice could not interpret this; please say it another way."

  def turnId(conversationId: String, seq: Int): String = s"$conversationId:turn-$seq"

  def create(context: WorkflowContext) = new ConversationTurnWorkflow(context)

  val extract           = step("extract")(_.extractStep)
  val apply             = step("apply")(_.applyStep)
  val couldNotInterpret = step("could-not-interpret")(_.couldNotInterpretStep)

  val start  = command("start")(_.start)
  val status = query("status")(_.status)
