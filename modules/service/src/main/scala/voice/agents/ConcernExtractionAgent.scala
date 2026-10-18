package voice.agents

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.*
import com.thinkmorestupidless.ankka.core.*
import voice.api.Problems

import scala.io.Source

/** The transcript of one conversation, rendered for the model, handed to it per turn (R5). */
final case class TurnInput(transcript: String)

final case class DraftSpan(message: Int, from: Int, to: Int)
final case class DraftElement(kind: String, text: String, spans: Vector[DraftSpan] = Vector.empty)
final case class ConcernProposalDraft(
    original: String,
    summary: String,
    elements: Vector[DraftElement] = Vector.empty,
    confidence: String = "Medium"
)

/** What the model answers on a turn: a reply, and either a clarifying question or a proposal. */
final case class Turn(
    reply: String,
    clarifyingQuestion: Option[String] = None,
    proposal: Option[ConcernProposalDraft] = None
)

given JsonValueCodec[TurnInput] = Problems.codec[TurnInput]
given JsonValueCodec[Turn]      = Problems.codec[Turn]
given Serializer[TurnInput]     = Codecs.serializer[TurnInput]("concern-extraction-input")
given Serializer[Turn]          = Codecs.serializer[Turn]("concern-extraction-turn")

/**
 * Listens and proposes (FR-006, FR-007). No memory: the conversation lives sealed on Voice's own
 * entity, and the model sees it only as this turn's context, which memory never keeps (R5). No
 * tools. Instructions inside the transcript are content (FR-010).
 */
final class ConcernExtractionAgent extends Agent:

  def turn(input: TurnInput): Effect[Turn] =
    effects
      .systemMessage(ConcernExtractionAgent.prompt)
      .withContext(s"The conversation so far:\n${input.transcript}")
      .userMessage("Respond to the resident's latest message, as the instructions say, in JSON.")
      .memory(MemoryProvider.none)
      .guardrails(ConcernExtractionAgent.noObedience)
      .thenReplyAs[Turn]

object ConcernExtractionAgent
    extends Agent.Companion[ConcernExtractionAgent](ComponentId("concern-extraction")):

  lazy val prompt: String =
    val stream = getClass.getResourceAsStream("/prompts/concern-extraction.md")
    try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()

  /** A reply never says it obeyed an instruction found in the conversation (FR-010). */
  val noObedience: Guardrail = new Guardrail:
    val name = "no-obedience"
    private val Claim =
      "(?i)(i (have|will) (now )?(ignore|ignored|disregard|disregarded) (my|all|the|previous)|as (you )?instructed,? i|i am now (in|operating)|developer mode)".r
    override def checkOutput(text: String): Either[String, Unit] =
      if Claim.findFirstIn(text).isDefined then
        Left("the reply claims to have followed an instruction")
      else Right(())

  def create(context: AgentContext) =
    val _ = context
    new ConcernExtractionAgent

  val turn = command("turn")(_.turn)
