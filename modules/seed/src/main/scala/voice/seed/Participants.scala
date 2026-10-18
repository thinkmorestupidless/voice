package voice.seed

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}

/** Voice's routes as anyone: `METHOD path body bearer => (status, json)`. */
trait VoiceApi:
  def send(
      method: String,
      path: String,
      body: Option[ujson.Value],
      bearer: Option[String]
  ): (Int, ujson.Value)

object VoiceApi:
  def http(baseUrl: String): VoiceApi = new VoiceApi:
    private val client =
      HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()
    def send(
        method: String,
        path: String,
        body: Option[ujson.Value],
        bearer: Option[String]
    ): (Int, ujson.Value) =
      val builder = HttpRequest
        .newBuilder(URI.create(baseUrl.stripSuffix("/") + path))
        .timeout(java.time.Duration.ofSeconds(60))
      bearer.foreach(t => builder.header("Authorization", s"Bearer $t"))
      body match
        case Some(json) =>
          builder.header("Content-Type", "application/json")
          builder.method(method, HttpRequest.BodyPublishers.ofString(ujson.write(json))): Unit
        case None => builder.method(method, HttpRequest.BodyPublishers.noBody()): Unit
      val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
      (response.statusCode, if response.body.isEmpty then ujson.Null else ujson.read(response.body))

/** What one synthetic participant did, by label. */
final case class Replayed(
    label: String,
    participant: String,
    contribution: Option[String],
    published: Boolean
)

/**
 * Replays the pack's synthetic participants through the citizen routes, as a resident would (tier
 * 4): a session from an invite code, one message, the proposal confirmed, the derivative shared, a
 * reviewer's approval into the Issue, and a priority. With enough of them the representative's
 * dashboard shows aggregates above the suppression threshold. Each run adds new participants.
 */
object Participants:

  final class ReplayFailed(message: String) extends RuntimeException(message)

  private val Modes = Map(
    "public-deidentified" -> "de-identified",
    "representative-only" -> "representative-only",
    "private"             -> "private"
  )

  private def ok(
      api: VoiceApi,
      method: String,
      path: String,
      body: Option[ujson.Value],
      bearer: Option[String]
  ): ujson.Value =
    val (status, json) = api.send(method, path, body, bearer)
    if status >= 300 then
      throw ReplayFailed(s"$method $path answered $status: ${ujson.write(json)}")
    json

  private def poll[A](what: String, seconds: Int)(attempt: => Option[A]): A =
    val deadline = System.currentTimeMillis() + seconds * 1000L
    var found    = attempt
    while found.isEmpty && System.currentTimeMillis() < deadline do
      Thread.sleep(500)
      found = attempt
    found.getOrElse(throw ReplayFailed(s"$what did not happen within ${seconds}s"))

  def replay(
      api: VoiceApi,
      reviewer: String,
      pack: Path,
      issue: String,
      invite: String
  ): Vector[Replayed] =
    ujson.read(Files.readString(pack.resolve("participants.json"))).arr.toVector.map { p =>
      val label   = p("label").str
      val session = ok(api, "POST", "/me/sessions", Some(ujson.Obj("invite" -> invite)), None)
      val me      = Some(session("session").str)
      val conv    = ok(api, "POST", "/me/conversations", Some(ujson.Obj()), me)("id").str
      val turn = ok(
        api,
        "POST",
        s"/me/conversations/$conv/messages",
        Some(ujson.Obj("text" -> p("message").str)),
        me
      )("turn").str
      val view = poll(s"$label's turn", 120) {
        val v = ok(api, "GET", s"/me/conversations/$conv/turns/$turn", None, me)
        Option.when(v("status").str != "Thinking")(v)
      }
      view.obj.get("proposal") match
        case None => Replayed(label, session("participant").str, None, published = false)
        case Some(proposal) =>
          val concern = ok(
            api,
            "POST",
            s"/me/conversations/$conv/proposals/${proposal("id").str}/confirm",
            Some(ujson.Obj()),
            me
          )("id").str
          val mode = Modes.getOrElse(p("mode").str, p("mode").str)
          val contribution =
            ok(
              api,
              "POST",
              s"/me/concerns/$concern/contributions",
              Some(ujson.Obj("mode" -> mode, "text" -> p("derivative").str)),
              me
            )("id").str
          val published =
            mode != "private" && {
              ok(
                api,
                "POST",
                s"/me/contributions/$contribution/submit",
                Some(ujson.Obj()),
                me
              ): Unit
              val staff = Some(reviewer)
              val item = poll(s"$label's review", 120) {
                ok(api, "GET", "/staff/reviews", None, staff).arr
                  .find(_("contribution").str == contribution)
                  .filter(_("status").str != "Checking")
              }
              item("status").str == "AwaitingReviewer" && {
                // A flagged derivative needs a second step; the seed leaves those for a person.
                val (status, _) = api.send(
                  "POST",
                  s"/staff/reviews/$contribution/approve",
                  Some(ujson.Obj("issue" -> issue)),
                  staff
                )
                status < 300
              }
            }
          ok(
            api,
            "POST",
            s"/me/issues/$issue/expression/priority",
            Some(ujson.Obj("level" -> p("priority").num.toInt)),
            me
          ): Unit
          Replayed(label, session("participant").str, Some(contribution), published)
    }
