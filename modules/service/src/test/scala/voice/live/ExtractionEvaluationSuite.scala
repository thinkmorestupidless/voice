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

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.concurrent.duration.DurationInt

/**
 * SC-012: the labelled set in `evaluation/concerns.json` against the live model (`just live`).
 *
 *   - **extraction accuracy**: the share of messages where the model proposed exactly when the
 *     label says it should, and a proposal holds every element kind the label names;
 *   - **correction rate**: the share of proposals a citizen would have to edit: an expected kind
 *     missing, or an expected phrase found in no element's text.
 *
 * It sets no threshold: it measures, writes `target/evaluation/extraction.md`, and that result is
 * copied into research.md "Measurements" with its date and commit.
 */
class ExtractionEvaluationSuite extends munit.FunSuite:

  override val munitTimeout = 1800.seconds

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

  private def prov(id: String) = Provenance(Actor.Participant(id), at, "evaluation", "evaluation")

  private def answer(n: Int, text: String): Option[Turn] =
    val participant = s"prt-eval-$n"
    val conv        = s"cnv-eval-$n"
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
    state.answer.flatMap(sealing.open[Turn](_, Purposes.Turn).toOption)

  test("extraction accuracy and correction rate over the labelled set".tag(Live)) {
    val set = ujson
      .read(getClass.getResourceAsStream("/evaluation/concerns.json").readAllBytes())
      .arr
      .toVector
    val rows = set.zipWithIndex.map { (item, n) =>
      val proposal = answer(n, item("message").str).flatMap(_.proposal)
      val proposes = item("proposes").bool
      val kinds    = item("kinds").arr.map(_.str).toSet
      val grounded = item("grounded").arr.map(_.str.toLowerCase)
      val got      = proposal.map(_.elements.map(_.kind).toSet).getOrElse(Set.empty)
      val texts = proposal
        .map(p => (p.summary +: p.elements.map(_.text)).mkString(" ").toLowerCase)
        .getOrElse("")
      val correct = proposal.isDefined == proposes && kinds.subsetOf(got)
      val edit =
        proposal.isDefined && (!kinds.subsetOf(got) || grounded.exists(g => !texts.contains(g)))
      (n, proposes, proposal.isDefined, correct, edit)
    }
    val accuracy   = rows.count(_._4).toDouble / rows.size
    val proposals  = rows.count(_._3)
    val correction = if proposals == 0 then 0.0 else rows.count(_._5).toDouble / proposals
    val report =
      s"""# Extraction evaluation
         |
         || Measure | Value |
         ||---|---|
         || messages | ${rows.size} |
         || extraction accuracy | ${f"$accuracy%.2f"} |
         || proposals | $proposals |
         || correction rate | ${f"$correction%.2f"} |
         |
         |${rows
          .map(r =>
            s"- item ${r._1}: expected ${if r._2 then "a proposal" else "none"}, got ${
                if r._3 then "a proposal" else "none"
              }, ${if r._4 then "correct" else "wrong"}${if r._5 then ", needs an edit" else ""}"
          )
          .mkString("\n")}
         |""".stripMargin
    val out = Path.of("target/evaluation/extraction.md")
    Files.createDirectories(out.getParent)
    Files.writeString(out, report)
    println(report)
    assert(rows.nonEmpty)
  }
