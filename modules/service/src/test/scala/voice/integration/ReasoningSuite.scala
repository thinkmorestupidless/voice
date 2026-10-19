package voice.integration

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, TimerProbe}
import voice.{Settings, VoiceService}
import voice.api.Edge
import voice.identity.{SessionTokens, StaffAcl}
import voice.seed.{Seed, StaffApi}
import voice.support.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Paths
import java.time.{Duration, Instant}
import scala.concurrent.duration.DurationInt

object Reasoning extends munit.Tag("Reasoning")

/**
 * Tier 3 (T068): Voice against the real reasoning service, as `docker compose --profile reasoning`
 * runs it (`just tier3`). V4, the writes are idempotent by id through the real service; V5, every
 * reference Voice links resolves by id; V6, the seed pack yields the contradiction and the
 * abstention from records alone. With `VOICE_GRAPH=1` and the graph profile, `learned` answers.
 * Excluded from `sbt test` by its tag; it needs `VOICE_REASONING_URL`.
 */
class ReasoningSuite extends munit.FunSuite:

  override def munitTimeout = scala.concurrent.duration.Duration(300, "s")

  private val url = sys.env.getOrElse("VOICE_REASONING_URL", "http://127.0.0.1:9100")

  private val http = HttpClient.newHttpClient()

  /** Started by the first test that runs, so an excluded suite starts nothing. */
  private lazy val started: (AnkkaTestKit, HttpServer) =
    val settings =
      Settings(SessionKeyForTests, 12, Set("pilot"), Duration.ofDays(180), 5, "reasoning")
    val edge = Edge(
      settings,
      voice.Clock.system,
      SessionTokens(settings.sessionKey, 12),
      StaffAcl.wrap(TestIssuer.acl)
    )
    val agents = VoiceService.agents(RecordedModelProvider("features"))
    val server = HttpServer.at("127.0.0.1", 0)(VoiceService.endpoints(edge)*)
    val kit = AnkkaTestKit.start(
      VoiceService.components(agents),
      VoiceService
        .extensions(agents, Timers.runtime(SettableClock(Instant.now()), TimerProbe()), server),
      readyTimeout = 120.seconds,
      localServices = Map("reasoning" -> url)
    )
    (kit, server)

  private var running = false

  override def afterAll(): Unit = if running then started._1.stop()

  private lazy val SessionKeyForTests = voice.domain.SealedCipher.newKey()

  private def base =
    running = true
    s"http://127.0.0.1:${started._2.boundPort.get}"

  private def get(path: String): (Int, ujson.Value) =
    val r = http.send(
      HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, if r.body.isEmpty then ujson.Null else ujson.read(r.body))

  private def reasoning(path: String): Int =
    http
      .send(
        HttpRequest.newBuilder(URI.create(url + path)).GET().build(),
        HttpResponse.BodyHandlers.discarding()
      )
      .statusCode

  private lazy val api  = StaffApi.http(base, TestIssuer.token("seed-reviewer", "reviewer"))
  private lazy val pack = Paths.get("seed/school-transport")
  private lazy val loaded =
    Seed.load(api, pack, title = Some(s"Rural school bus withdrawal ${Instant.now().toEpochMilli}"))

  test(
    "V4: the seed pack loads through the real service, and again without change".tag(Reasoning)
  ) {
    val again = Seed.load(api, pack, existing = Some(loaded.issue))
    assertEquals(again, loaded)
  }

  test("V5: every reference the Issue links resolves in reasoning by id".tag(Reasoning)) {
    for q <- loaded.questions.values do assertEquals(reasoning(s"/questions/$q"), 200, q)
    for c <- loaded.claims.values do assertEquals(reasoning(s"/claims/$c"), 200, c)
    val (_, evidence) = api.send("GET", s"/staff/issues/${loaded.issue}/evidence", None)
    for e <- evidence.arr if e("card")("state").str == "accepted" do
      assertEquals(reasoning(s"/evidence/${e("card")("evidence").str}"), 200, e.toString)
  }

  test("V6: the contradiction and the abstention come from records alone".tag(Reasoning)) {
    val (status, c1) = get(s"/public/issues/${loaded.issue}/claims/${loaded.claims("c1")}")
    assertEquals(status, 200, c1.toString)
    assert(c1("contradicting").arr.exists(_("id").str == loaded.claims("c2")), c1.toString)
    val (_, q2) = get(s"/public/issues/${loaded.issue}/questions/${loaded.questions("q2")}")
    assert(q2("abstained").bool, q2.toString)
  }

  test("with the graph tier, learned answers".tag(Reasoning)) {
    assume(sys.env.get("VOICE_GRAPH").contains("1"), "the graph profile is not running")
    val q = java.net.URLEncoder.encode(loaded.questions("q1"), "UTF-8")
    assertEquals(reasoning(s"/answers/learned?question=$q&after=2020-01-01T00:00:00Z"), 200)
  }
