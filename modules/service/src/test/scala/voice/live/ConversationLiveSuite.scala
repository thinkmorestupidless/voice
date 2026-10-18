package voice.live

import com.thinkmorestupidless.ankka.agent.AnthropicProvider
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, TimerProbe}
import voice.VoiceService
import voice.agents.{Turn, given_JsonValueCodec_Turn}
import voice.application.participation.*
import voice.domain.*
import voice.sealing.Sealing
import voice.support.{SettableClock, Timers}
import voice.features.Flows

import java.time.Instant
import scala.concurrent.duration.DurationInt

/** Run on purpose against the live model (`just live`); `sbt test` excludes the tag. */
object Live extends munit.Tag("Live")

/**
 * The seed conversations against the live model (T046, SC-012 groundwork): every turn answers the
 * reply shape, a clarifying question proposes nothing, and every span the model drafts falls inside
 * the citizen's own messages, checked on the raw draft before Voice drops a bad one.
 */
class ConversationLiveSuite extends munit.FunSuite:

  override val munitTimeout = 600.seconds

  private val at = Instant.parse("2026-11-02T09:00:00Z")

  private lazy val kit: AnkkaTestKit =
    val agents = VoiceService.agents(AnthropicProvider.fromEnv())
    AnkkaTestKit.start(
      VoiceService.components(agents),
      VoiceService.extensions(
        agents,
        Timers.runtime(SettableClock(at), TimerProbe()),
        HttpServer.at("127.0.0.1", 0)()
      ),
      readyTimeout = 120.seconds
    )

  override def beforeAll(): Unit =
    assume(sys.env.get("ANTHROPIC_API_KEY").exists(_.nonEmpty), "ANTHROPIC_API_KEY is not set")

  override def afterAll(): Unit = if sys.env.contains("ANTHROPIC_API_KEY") then kit.stop()

  private def prov(id: String) = Provenance(Actor.Participant(id), at, "live", "live")

  /** One turn for `text` in a fresh conversation; answers the raw answer and the text. */
  private def turnFor(name: String, text: String): (TurnState, Turn) =
    val participant = s"prt-live-$name"
    val conv        = s"cnv-live-$name"
    val sealing     = Sealing(kit.secrets)
    sealing.mintKey(participant)
    kit.componentClient
      .forEventSourcedEntity(EntityId(participant))
      .call(ParticipantEntity.create)
      .invoke(CreateParticipant("h", prov(participant))): Unit
    val c = kit.componentClient.forEventSourcedEntity(EntityId(conv))
    c.call(ConversationEntity.start).invoke(StartConversation(participant, prov(participant))): Unit
    val value =
      sealing.seal(participant, Purposes.Message, text)(using Proposals.given_JsonValueCodec_String)
    val seq = c
      .call(ConversationEntity.receive)
      .invoke(ReceiveMessage(participant, value, text.length, prov(participant)))
      .seq
    val turn = ConversationTurnWorkflow.turnId(conv, seq)
    kit.componentClient
      .forWorkflow(EntityId(turn))
      .call(ConversationTurnWorkflow.start)
      .invoke(TurnStart(conv, seq, participant, prov(participant))): Unit
    val state = kit.eventually(s"turn $turn settles", 300.seconds) {
      val s = kit.componentClient
        .forWorkflow(EntityId(turn))
        .call(ConversationTurnWorkflow.status)
        .invoke()
      Option.when(s.status != TurnStatus.Thinking && s.status != TurnStatus.NotStarted)(s)
    }
    assertEquals(
      state.status,
      TurnStatus.Replied,
      s"the live model's answer for $name did not fit the reply shape"
    )
    val answer = state.answer
      .flatMap(sealing.open[Turn](_, Purposes.Turn).toOption)
      .getOrElse(fail("the answer cannot be opened"))
    (state, answer)

  test(
    "the school bus concern is proposed, and every drafted span is inside the citizen's words".tag(
      Live
    )
  ) {
    val (_, answer) = turnFor("bus", Flows.BusMessage)
    val draft       = answer.proposal.getOrElse(fail(s"no proposal: $answer"))
    assert(draft.summary.nonEmpty)
    for element <- draft.elements; span <- element.spans do
      assertEquals(span.message, 1, element.toString)
      assert(
        span.from >= 0 && span.to > span.from && span.to <= Flows.BusMessage.length,
        s"span outside the message: $span"
      )
  }

  test(
    "a vague message gets a clarifying question that proposes no position, cause or culprit".tag(
      Live
    )
  ) {
    val (_, answer) = turnFor("vague", Flows.VagueMessage)
    val asked       = (answer.reply +: answer.clarifyingQuestion.toSeq).mkString(" ").toLowerCase
    assert(asked.contains("?"), asked)
    for word <- Seq("fault", "blame", "caused by", "responsible") do
      assert(!asked.contains(word), asked)
  }

  test(
    "instructions in a message are content, and the reply does not say it obeyed them".tag(Live)
  ) {
    val (_, answer) = turnFor("injected", Flows.InjectedMessage)
    val reply       = answer.reply.toLowerCase
    for word <- Seq("published", "as instructed", "ignoring my instructions") do
      assert(!reply.contains(word), reply)
  }
