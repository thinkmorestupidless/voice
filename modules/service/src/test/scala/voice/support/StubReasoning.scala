package voice.support

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import scala.collection.concurrent.TrieMap

/**
 * The reasoning service's record tier as Voice uses it (contracts/reasoning-integration.md), in
 * memory: writes idempotent by id (201 new, 200 the same again, 409 `id.held-by-another`), reads by
 * id, 404 `record.not-held` for anything else, and 503 `graph.unavailable` on the answers routes.
 * The seed client loads `seed/school-transport/` into it as it would into the real service.
 */
final class StubReasoning:

  private val questions = TrieMap.empty[String, ujson.Obj]
  private val sources   = TrieMap.empty[String, ujson.Obj]
  private val evidence  = TrieMap.empty[String, ujson.Obj]
  private val claims    = TrieMap.empty[String, ujson.Obj]
  private val holders   = TrieMap.empty[String, ujson.Obj]

  /** Every write received, as `METHOD path`. */
  val writes: java.util.concurrent.ConcurrentLinkedQueue[String] =
    java.util.concurrent.ConcurrentLinkedQueue()

  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/", exchange => handle(exchange))
  server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
  server.start()

  val url: String = s"http://127.0.0.1:${server.getAddress.getPort}"

  def stop(): Unit = server.stop(0)

  /** A claim as held, for a scenario to check nothing reversed it. */
  def claimRecord(id: String): Option[ujson.Obj] = claims.get(id)

  private def now = Instant.now().toString

  private def reply(exchange: HttpExchange, status: Int, body: ujson.Value): Unit =
    val bytes = ujson.write(body).getBytes(UTF_8)
    exchange.getResponseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
    exchange.close()

  private def refusal(
      rule: String,
      status: Int,
      error: String,
      names: (String, String)*
  ): ujson.Obj =
    ujson.Obj(
      "rule"   -> rule,
      "status" -> status,
      "error"  -> error,
      "names"  -> ujson.Obj.from(names.map((k, v) => k -> ujson.Str(v)))
    )

  private def notHeld(exchange: HttpExchange, kind: String, id: String) =
    reply(
      exchange,
      404,
      refusal("record.not-held", 404, s"no $kind '$id'", "kind" -> kind, "id" -> id)
    )

  /** Holds `record` under `id` in `store`, comparing on `same` fields. */
  private def hold(
      exchange: HttpExchange,
      store: TrieMap[String, ujson.Obj],
      kind: String,
      id: String,
      record: ujson.Obj,
      same: Seq[String]
  ): Unit =
    store.putIfAbsent(id, record) match
      case None => reply(exchange, 201, record)
      case Some(held) =>
        if same.forall(k => held.value.get(k) == record.value.get(k)) then
          reply(exchange, 200, held)
        else
          reply(
            exchange,
            409,
            refusal(
              "id.held-by-another",
              409,
              s"$kind '$id' is held with other content",
              "kind" -> kind,
              "id"   -> id
            )
          )

  private def handle(exchange: HttpExchange): Unit =
    try
      val method = exchange.getRequestMethod
      val path   = java.net.URLDecoder.decode(exchange.getRequestURI.getRawPath, "UTF-8")
      val parts  = path.split('/').filter(_.nonEmpty).toList
      def body   = ujson.read(String(exchange.getRequestBody.readAllBytes(), UTF_8)).obj
      if method == "POST" then writes.add(s"POST $path"): Unit
      (method, parts) match
        case ("POST", List("holders")) =>
          val b = body
          hold(exchange, holders, "holder", b("id").str, b, Seq("kind", "name"))
        case ("POST", List("sources")) =>
          val b = body
          hold(
            exchange,
            sources,
            "source",
            b("id").str,
            ujson.Obj(
              "id"         -> b("id"),
              "name"       -> b("name"),
              "dated"      -> now,
              "recordedAt" -> now,
              "writer"     -> "local"
            ),
            Seq("name")
          )
        case ("POST", List("questions")) =>
          val b = body
          val record = ujson.Obj(
            "id"        -> b("id"),
            "statement" -> b("statement"),
            "hypotheses" -> ujson.Arr.from(
              b("hypotheses").arr.map(h =>
                ujson.Obj(
                  "id"         -> h("id"),
                  "statement"  -> h("statement"),
                  "dated"      -> now,
                  "recordedAt" -> now,
                  "writer"     -> "local"
                )
              )
            ),
            "dated"      -> now,
            "recordedAt" -> now,
            "writer"     -> "local"
          )
          hold(exchange, questions, "question", b("id").str, record, Seq("statement"))
        case ("POST", List("evidence")) =>
          val b      = body
          val source = b("source").str
          if !sources.contains(source) then notHeld(exchange, "source", source)
          else
            val id = StubReasoning.digest(source, b("locator").str, b("excerpt").str)
            val record = ujson.Obj(
              "id"         -> id,
              "source"     -> source,
              "locator"    -> b("locator"),
              "excerpt"    -> b("excerpt"),
              "dated"      -> now,
              "recordedAt" -> now,
              "writer"     -> "local"
            )
            b.value.get("publishedAt").foreach(p => record("publishedAt") = p)
            b.value.get("author").foreach(a => record("author") = a)
            hold(exchange, evidence, "evidence", id, record, Seq("source", "locator", "excerpt"))
        case ("POST", List("evidence", id, "withdrawal")) =>
          val b = body
          evidence.get(id) match
            case None => notHeld(exchange, "evidence", id)
            case Some(held) =>
              val updated = ujson.Obj.from(
                held.value.filterNot((k, _) => k == "excerpt" || k == "locator" || k == "author")
              )
              updated("withdrawal") = ujson.Obj(
                "note"   -> b.value.getOrElse("note", ujson.Str("")),
                "writer" -> "local",
                "at"     -> now
              )
              evidence.put(id, updated)
              reply(exchange, 200, updated)
        case ("POST", List("claims")) =>
          val b       = body
          val missing = b("derivesFrom").arr.map(_.str).find(e => !evidence.contains(e))
          missing match
            case Some(e) => notHeld(exchange, "evidence", e)
            case None =>
              val record = ujson.Obj(
                "id"          -> b("id"),
                "holder"      -> b("holder"),
                "statement"   -> b("statement"),
                "derivesFrom" -> b("derivesFrom"),
                "stances"     -> b("stances"),
                "dated"       -> now,
                "recordedAt"  -> now,
                "writer"      -> "local"
              )
              b.value.get("revises").foreach(r => record("revises") = r)
              hold(
                exchange,
                claims,
                "claim",
                b("id").str,
                record,
                Seq("statement", "derivesFrom", "stances", "revises")
              )
        case ("GET", List("questions", id)) =>
          questions.get(id).fold(notHeld(exchange, "question", id))(reply(exchange, 200, _))
        case ("GET", List("sources", id)) =>
          sources.get(id).fold(notHeld(exchange, "source", id))(reply(exchange, 200, _))
        case ("GET", List("evidence", id)) =>
          evidence.get(id).fold(notHeld(exchange, "evidence", id))(reply(exchange, 200, _))
        case ("GET", List("claims", id)) =>
          claims.get(id) match
            case None => notHeld(exchange, "claim", id)
            case Some(c) =>
              val revisedBy =
                claims.values.filter(_.value.get("revises").exists(_.str == id)).map(_("id")).toSeq
              reply(
                exchange,
                200,
                ujson.Obj("claim" -> c, "revisedBy" -> ujson.Arr.from(revisedBy))
              )
        case (_, "answers" :: _) =>
          reply(exchange, 503, refusal("graph.unavailable", 503, "no graph tier"))
        case _ => reply(exchange, 404, refusal("route.not-found", 404, s"$method $path"))
    catch
      case e: Throwable =>
        reply(exchange, 500, ujson.Obj("error" -> String.valueOf(e.getMessage)))

object StubReasoning:
  def digest(source: String, locator: String, excerpt: String): String =
    java.util.HexFormat
      .of()
      .formatHex(
        MessageDigest
          .getInstance("SHA-256")
          .digest(s"$source\n$locator\n$excerpt\n".getBytes(UTF_8))
      )
