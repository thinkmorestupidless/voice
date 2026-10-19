package voice.unit

import com.thinkmorestupidless.ankka.agent.TestModelProvider
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import voice.VoiceService
import voice.application.participation.*
import voice.domain.*
import voice.domain.participation.*
import voice.sealing.Sealing
import voice.support.{SettableClock, Timers}

import java.time.Instant
import scala.concurrent.duration.DurationInt

/**
 * The turn workflow against a scripted model (T040): a reply with a proposal, a span outside the
 * citizen's words dropped, an answer that is not the reply shape failing over to the honest "could
 * not interpret", and a repeated start addressing the running turn (R7).
 */
class ConversationTurnWorkflowSuite extends munit.FunSuite:

  override val munitTimeout = 180.seconds

  private val model = TestModelProvider()
  private val at    = Instant.parse("2026-11-02T09:00:00Z")

  private lazy val kit: AnkkaTestKit =
    val agents = VoiceService.agents(model)
    AnkkaTestKit.start(
      VoiceService.components(agents),
      VoiceService.extensions(
        agents,
        Timers.runtime(SettableClock(at), com.thinkmorestupidless.ankka.testkit.TimerProbe()),
        com.thinkmorestupidless.ankka.http.HttpServer.at("127.0.0.1", 0)()
      ),
      readyTimeout = 120.seconds
    )

  override def afterAll(): Unit = kit.stop()

  private def prov(id: String) = Provenance(Actor.Participant(id), at, "turn-suite", "turn-suite")

  /** A participant with a key, a conversation, and one citizen message; answers the turn id. */
  private def said(participant: String, conversation: String, text: String): String =
    val sealing = Sealing(kit.secrets)
    sealing.mintKey(participant)
    kit.componentClient
      .forEventSourcedEntity(EntityId(participant))
      .call(ParticipantEntity.create)
      .invoke(CreateParticipant("h", prov(participant))): Unit
    val c = kit.componentClient.forEventSourcedEntity(EntityId(conversation))
    c.call(ConversationEntity.start).invoke(StartConversation(participant, prov(participant))): Unit
    val sealedText =
      sealing.seal(participant, Purposes.Message, text)(using Proposals.given_JsonValueCodec_String)
    val seq = c
      .call(ConversationEntity.receive)
      .invoke(ReceiveMessage(participant, sealedText, text.length, prov(participant)))
      .seq
    val turn = ConversationTurnWorkflow.turnId(conversation, seq)
    kit.componentClient
      .forWorkflow(EntityId(turn))
      .call(ConversationTurnWorkflow.start)
      .invoke(TurnStart(conversation, seq, participant, prov(participant))): Unit
    turn

  private def settled(turn: String): TurnState =
    kit.eventually(s"turn $turn settles", 90.seconds) {
      val state = kit.componentClient
        .forWorkflow(EntityId(turn))
        .call(ConversationTurnWorkflow.status)
        .invoke()
      Option.when(state.status != TurnStatus.Thinking && state.status != TurnStatus.NotStarted)(
        state
      )
    }

  private def held(conversation: String) =
    kit.componentClient
      .forEventSourcedEntity(EntityId(conversation))
      .call(ConversationEntity.get)
      .invoke()

  test("a reply with a proposal is recorded, and a span outside the citizen's words is dropped") {
    val text = "The streetlight on Mill Lane has been out for three weeks."
    model.whenUserSays("Mill Lane")(
      ujson.write(
        ujson.Obj(
          "reply" -> "Here is what I understood.",
          "proposal" -> ujson.Obj(
            "original" -> text,
            "summary"  -> "A streetlight on Mill Lane has been out for three weeks.",
            "elements" -> ujson.Arr(
              ujson.Obj(
                "kind"  -> "Problem",
                "text"  -> "streetlight out",
                "spans" -> ujson.Arr(ujson.Obj("message" -> 1, "from" -> 4, "to" -> 15))
              ),
              ujson.Obj(
                "kind"  -> "Where",
                "text"  -> "invented",
                "spans" -> ujson.Arr(ujson.Obj("message" -> 1, "from" -> 40, "to" -> 900))
              )
            ),
            "confidence" -> "high"
          )
        )
      )
    ): Unit
    val turn  = said("prt-turn-a", "cnv-turn-a", text)
    val state = settled(turn)
    assertEquals(state.status, TurnStatus.Replied)
    val conversation = held("cnv-turn-a")
    assertEquals(conversation.messages.map(_.author), Vector(Author.Citizen, Author.Voice))
    val proposal = conversation.proposals.head
    assertEquals(state.proposalId, Some(proposal.proposalId))
    assertEquals(proposal.elements.map(_.kind), Vector(ElementKind.Problem))
    assertEquals(proposal.confidence, Confidence.High)
    assert(conversation.repliedTurns.contains(turn))
  }

  test("an answer that is not the reply shape fails over to saying so, never silently") {
    model.whenUserSays("Ferndale")("I am not JSON at all."): Unit
    val turn  = said("prt-turn-b", "cnv-turn-b", "The bins on Ferndale Road were not collected.")
    val state = settled(turn)
    assertEquals(state.status, TurnStatus.CouldNotInterpret)
    val conversation = held("cnv-turn-b")
    assertEquals(conversation.proposals.size, 0)
    val sealing = Sealing(kit.secrets)
    val last    = conversation.messages.last
    assertEquals(last.author, Author.Voice)
    assertEquals(
      sealing.openText(last.text, Purposes.Message)(using Proposals.given_JsonValueCodec_String),
      ConversationTurnWorkflow.CouldNotInterpret
    )
  }

  test("a repeated start addresses the running turn and records one reply") {
    model.whenUserSays("Orchard")(
      ujson.write(ujson.Obj("reply" -> "Which part of Orchard Street?"))
    ): Unit
    val turn = said("prt-turn-c", "cnv-turn-c", "The pavement on Orchard Street is broken.")
    kit.componentClient
      .forWorkflow(EntityId(turn))
      .call(ConversationTurnWorkflow.start)
      .invoke(TurnStart("cnv-turn-c", 1, "prt-turn-c", prov("prt-turn-c"))): Unit
    settled(turn): Unit
    assertEquals(held("cnv-turn-c").messages.count(_.author == Author.Voice), 1)
  }
