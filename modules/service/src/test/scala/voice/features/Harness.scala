package voice.features

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, TimerProbe}
import voice.{Settings, VoiceService}
import voice.api.Edge
import voice.identity.{SessionTokens, StaffAcl}
import voice.support.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/** What the service answered: status, body and headers. */
final case class Answer(status: Int, body: String, headers: Map[String, String] = Map.empty):
  def json: ujson.Value = if body.isEmpty then ujson.Null else ujson.read(body)
  def rule: String = scala.util.Try(json.obj.get("rule").map(_.str).getOrElse("")).getOrElse("")
  def header(name: String): Option[String] = headers.collectFirst {
    case (k, v) if k.equalsIgnoreCase(name) => v
  }
  override def toString: String = s"$status $body"

/**
 * The whole service for scenarios (R17): one per test JVM, started the first time a suite asks. The
 * recorded model, the in-suite staff issuer, the in-memory reasoning, the settable clock and
 * short-poll timers, against a throwaway Postgres. Suites share it and keep out of each other's way
 * by naming their participants and issues after their own scenario.
 */
object Harness:

  val Start: Instant = Instant.parse("2026-11-02T09:00:00Z")

  val clock: SettableClock = SettableClock(Start)
  val probe: TimerProbe    = TimerProbe()
  val model                = RecordedModelProvider("features")
  val reasoning            = StubReasoning()

  val settings: Settings = Settings(
    sessionKey = voice.domain.SealedCipher.newKey(),
    sessionHours = 12,
    inviteCodes = Set("pilot"),
    dormancyPeriod = Duration.ofDays(180),
    suppressionThreshold = 5,
    reasoningService = "reasoning"
  )

  /** The service's timers, made before the edge so the edge can schedule on them (FR-063). */
  val timers = Timers.runtime(clock, probe)

  /**
   * While set, participants starting a session get this dormancy period instead of the setting's.
   */
  @volatile var dormancy: Option[Duration] = None

  val edge: Edge = Edge(
    settings,
    clock,
    SessionTokens(settings.sessionKey, settings.sessionHours),
    StaffAcl.wrap(TestIssuer.acl),
    () => Some(timers.timerScheduler),
    () => dormancy
  )

  private lazy val started: (AnkkaTestKit, HttpServer) =
    val agents = VoiceService.agents(model)
    val server = HttpServer.at("127.0.0.1", 0)(VoiceService.endpoints(edge)*)
    val kit = AnkkaTestKit.start(
      VoiceService.components(agents),
      VoiceService.extensions(agents, timers, server),
      readyTimeout = 120.seconds,
      localServices = Map("reasoning" -> reasoning.url)
    )
    probe.bind(kit)
    sys.addShutdownHook { kit.stop(); reasoning.stop() }: Unit
    (kit, server)

  def kit: AnkkaTestKit = started._1

  def baseUrl: String =
    s"http://127.0.0.1:${started._2.boundPort.getOrElse(sys.error("the server did not bind"))}"

  /** Drops every entity from memory and starts again on the same database (FR-054). */
  def restart(): Unit = kit.restartService()

  private val client =
    HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()

  def send(
      method: String,
      path: String,
      body: Option[ujson.Value] = None,
      bearer: Option[String] = None
  ): Answer =
    val builder =
      HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(java.time.Duration.ofSeconds(60))
    bearer.foreach(t => builder.header("Authorization", s"Bearer $t"))
    body match
      case Some(json) =>
        builder.header("Content-Type", "application/json")
        builder.method(method, HttpRequest.BodyPublishers.ofString(ujson.write(json))): Unit
      case None => builder.method(method, HttpRequest.BodyPublishers.noBody()): Unit
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    val headers = response.headers().map().asScala.map((k, v) => k -> v.asScala.mkString(",")).toMap
    Answer(response.statusCode, response.body, headers)
