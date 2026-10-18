package voice.seed

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path, Paths}

/** What a load produced: the Issue, and each pack key's id in Voice or reasoning. */
final case class Loaded(
    issue: String,
    questions: Map[String, String],
    evidence: Map[String, String],
    claims: Map[String, String],
    options: Map[String, String] = Map.empty
)

/** The staff routes the seed calls, as `METHOD path body => (status, json)`. */
trait StaffApi:
  def send(method: String, path: String, body: Option[ujson.Value]): (Int, ujson.Value)

object StaffApi:
  /** Voice at `baseUrl`, as the reviewer whose token is `token`. */
  def http(baseUrl: String, token: String): StaffApi = new StaffApi:
    private val client =
      HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()
    def send(method: String, path: String, body: Option[ujson.Value]): (Int, ujson.Value) =
      val builder = HttpRequest
        .newBuilder(URI.create(baseUrl.stripSuffix("/") + path))
        .timeout(java.time.Duration.ofSeconds(60))
      builder.header("Authorization", s"Bearer $token")
      body match
        case Some(json) =>
          builder.header("Content-Type", "application/json")
          builder.method(method, HttpRequest.BodyPublishers.ofString(ujson.write(json))): Unit
        case None => builder.method(method, HttpRequest.BodyPublishers.noBody()): Unit
      val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
      (response.statusCode, if response.body.isEmpty then ujson.Null else ujson.read(response.body))

/**
 * Loads a seed pack into Voice through the staff routes, as a reviewer (T061). Voice writes to
 * reasoning as its curation does, reasoning first and the Issue second, so the seed takes the path
 * a reviewer's work takes and adds nothing Voice did not decide. Every step is idempotent: sources
 * and questions by id, evidence by source and locator, claims by key, so a second run changes
 * nothing.
 */
object Seed:

  final class SeedFailed(message: String) extends RuntimeException(message)

  private def read(pack: Path, name: String): ujson.Value =
    ujson.read(Files.readString(pack.resolve(name)))

  private def ok(
      api: StaffApi,
      method: String,
      path: String,
      body: Option[ujson.Value]
  ): ujson.Value =
    val (status, json) = api.send(method, path, body)
    if status >= 300 then throw SeedFailed(s"$method $path answered $status: ${ujson.write(json)}")
    json

  /**
   * Loads `pack`; `title` names the Issue, so a test can load the pack beside another copy of it.
   */
  def load(
      api: StaffApi,
      pack: Path,
      title: Option[String] = None,
      existing: Option[String] = None
  ): Loaded =
    val issueSpec = read(pack, "issue.json")
    val issue = existing.getOrElse {
      ok(
        api,
        "POST",
        "/staff/issues",
        Some(
          ujson.Obj(
            "title"   -> title.getOrElse(issueSpec("title").str),
            "summary" -> issueSpec("summary").str
          )
        )
      )("id").str
    }

    for s <- read(pack, "sources.json").arr do ok(api, "POST", "/staff/sources", Some(s)): Unit

    val linked = issueSpec("questions").arr.map(_.str).toSet
    val questions = read(pack, "questions.json").arr
      .filter(q => linked(q("key").str))
      .map { q =>
        q("key").str -> ok(api, "POST", s"/staff/issues/$issue/questions", Some(q))("id").str
      }
      .toMap

    val held = ok(api, "GET", s"/staff/issues/$issue/evidence", None).arr
    def heldLink(source: String, locator: String) =
      held.find(h =>
        h("card")("source")("id").str == source && h("card").obj
          .get("locator")
          .exists(_.str == locator)
      )

    val evidence = read(pack, "evidence.json").arr.map { e =>
      val body = ujson.Obj.from(e.obj.filterNot((k, _) => k == "key" || k == "accept"))
      val link = heldLink(e("source").str, e("locator").str).getOrElse(
        ok(api, "POST", s"/staff/issues/$issue/evidence", Some(body))
      )
      val id = link("card")("id").str
      if e("accept").bool && link("card")("state").str == "candidate" then
        ok(api, "POST", s"/staff/issues/$issue/evidence/$id/accept", Some(ujson.Obj())): Unit
      e("key").str -> id
    }.toMap

    val claims = read(pack, "claims.json").arr.map { c =>
      val body = ujson.Obj(
        "key"         -> c("key"),
        "statement"   -> c("statement"),
        "derivesFrom" -> ujson.Arr.from(c("derivesFrom").arr.map(k => ujson.Str(evidence(k.str)))),
        "stances"     -> c("stances")
      )
      c("key").str -> ok(api, "POST", s"/staff/issues/$issue/claims", Some(body))("id").str
    }.toMap

    // Options by title: the Issue's page lists those it has, so a second run adds none.
    val described = ok(api, "GET", s"/staff/issues/$issue/options", None).arr
      .map(o => o("title").str -> o("id").str)
      .toMap
    val options = read(pack, "options.json").arr.map { o =>
      o("title").str -> described.getOrElse(
        o("title").str,
        ok(api, "PUT", s"/staff/issues/$issue/options/new", Some(o))("id").str
      )
    }.toMap

    Loaded(issue, questions, evidence, claims, options)

  /**
   * `just seed`: `VOICE_URL` (default `http://localhost:9000`) and `SEED_STAFF_TOKEN`, a reviewer's
   * token.
   */
  def main(args: Array[String]): Unit =
    val url = sys.env.getOrElse("VOICE_URL", "http://localhost:9000")
    val token = sys.env.getOrElse(
      "SEED_STAFF_TOKEN",
      throw SeedFailed("SEED_STAFF_TOKEN is not set: a reviewer's token from the staff issuer.")
    )
    val pack = Paths.get(args.headOption.getOrElse("seed/school-transport"))
    val loaded =
      load(StaffApi.http(url, token), pack, existing = sys.env.get("SEED_ISSUE").filter(_.nonEmpty))
    println(
      s"issue ${loaded.issue}: ${loaded.questions.size} questions, ${loaded.evidence.size} evidence, ${loaded.claims.size} claims"
    )
    // SEED_INVITE replays the synthetic participants too, through the citizen routes; needs the model.
    for invite <- sys.env.get("SEED_INVITE").filter(_.nonEmpty) do
      val replayed = Participants.replay(VoiceApi.http(url), token, pack, loaded.issue, invite)
      for r <- replayed do
        println(s"${r.label}: ${if r.published then "published" else "not published"}")
