package voice.agents

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.*
import com.thinkmorestupidless.ankka.core.*
import voice.api.Problems

import scala.io.Source

/** The derivative to check, as the participant approved it. */
final case class PrivacyInput(text: String)

/** The model's flags: names of `PrivacyFlag` cases, and a reason in words for each. */
final case class PrivacyFlagsReply(
    flags: Vector[String] = Vector.empty,
    reasons: Vector[String] = Vector.empty
)

given JsonValueCodec[PrivacyInput]      = Problems.codec[PrivacyInput]
given JsonValueCodec[PrivacyFlagsReply] = Problems.codec[PrivacyFlagsReply]
given Serializer[PrivacyInput]          = Codecs.serializer[PrivacyInput]("privacy-flagging-input")
given Serializer[PrivacyFlagsReply] = Codecs.serializer[PrivacyFlagsReply]("privacy-flagging-reply")

/**
 * The judgement half of the privacy check (FR-016, R6): one typed call, no memory, no tools. The
 * deterministic detectors run first; this adds what a pattern cannot see. It can only add flags.
 */
final class PrivacyFlaggingAgent extends Agent:

  def check(input: PrivacyInput): Effect[PrivacyFlagsReply] =
    effects
      .systemMessage(PrivacyFlaggingAgent.prompt)
      .userMessage(s"The text to review:\n<<<\n${input.text}\n>>>")
      .memory(MemoryProvider.none)
      .thenReplyAs[PrivacyFlagsReply]

object PrivacyFlaggingAgent
    extends Agent.Companion[PrivacyFlaggingAgent](ComponentId("privacy-flagging")):

  lazy val prompt: String =
    val stream = getClass.getResourceAsStream("/prompts/privacy-flagging.md")
    try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()

  def create(context: AgentContext) =
    val _ = context
    new PrivacyFlaggingAgent

  val check = command("check")(_.check)
