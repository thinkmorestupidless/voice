package voice.support

import com.thinkmorestupidless.ankka.agent.*
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.NotUsed

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

/**
 * The model for tests (R16, V8): replays `src/test/resources/model/<suite>/` and calls no model.
 *
 * A request is rendered as canonical text (system message, then each turn by role) and looked up
 * two ways. First by the SHA-256 of that text, `<sha>.json`, which is what recording writes. Then
 * by the fixtures that say what they answer: a fixture with `when` (every string must appear in the
 * request) and optionally `unless` (none may) answers any request it matches, the most specific
 * (the most `when` strings) first. A request no fixture answers fails, naming its digest and the
 * end of its text, so the fixture to write is obvious.
 *
 * Under `VOICE_MODEL_RECORD=1` a request with no fixture is sent to Anthropic through
 * `AnthropicProvider.fromEnv()` and its answer written as `<sha>.json`.
 *
 * A fixture is `{ "when": [...], "unless": [...], "text": "...", "toolCalls": [{ "id", "name",
 * "arguments": {...} }] }`.
 */
final class RecordedModelProvider(suite: String) extends ModelProvider:

  val name: String      = s"recorded:$suite"
  val modelName: String = "recorded"

  private val directory = Path.of("modules/service/src/test/resources/model", suite)
  private val recording = sys.env.get("VOICE_MODEL_RECORD").contains("1")
  private lazy val live = AnthropicProvider.fromEnv()

  /** Every request seen, in order, as its canonical text. */
  val requests: ConcurrentLinkedQueue[String] = ConcurrentLinkedQueue()

  private final case class Fixture(
      file: String,
      when: Seq[String],
      unless: Seq[String],
      response: ModelResponse
  )

  private def fixtures(): Seq[Fixture] =
    if !Files.isDirectory(directory) then Seq.empty
    else
      Files
        .list(directory)
        .iterator()
        .asScala
        .filter(_.toString.endsWith(".json"))
        .toSeq
        .sortBy(_.toString)
        .map(p => parse(p.getFileName.toString, ujson.read(Files.readString(p))))

  private def parse(file: String, json: ujson.Value): Fixture =
    val obj = json.obj
    val calls = obj.get("toolCalls").map(_.arr.toVector).getOrElse(Vector.empty).map { c =>
      ToolCall(
        c("id").str,
        c("name").str,
        Json.parse(ujson.write(c("arguments"))).fold(e => sys.error(e), identity)
      )
    }
    Fixture(
      file,
      obj.get("when").map(_.arr.map(_.str).toSeq).getOrElse(Seq.empty),
      obj.get("unless").map(_.arr.map(_.str).toSeq).getOrElse(Seq.empty),
      ModelResponse(
        text = obj.get("text").map(_.str).getOrElse(""),
        toolCalls = calls,
        stopReason = if calls.nonEmpty then StopReason.ToolUse else StopReason.EndTurn
      )
    )

  def complete(request: ModelRequest): Future[ModelResponse] =
    val text   = RecordedModelProvider.canonical(request)
    val digest = RecordedModelProvider.sha256(text)
    requests.add(text): Unit
    val exact = directory.resolve(s"$digest.json")
    if Files.exists(exact) then
      Future.successful(parse(exact.toString, ujson.read(Files.readString(exact))).response)
    else
      val matching = fixtures()
        .filter(f =>
          f.when.nonEmpty && f.when.forall(text.contains) && !f.unless.exists(text.contains)
        )
        .sortBy(f => -f.when.size)
      matching.headOption match
        case Some(f) => Future.successful(f.response)
        case None if recording =>
          import scala.concurrent.ExecutionContext.Implicits.global
          live.complete(request).map { response =>
            Files.createDirectories(directory)
            val json = ujson.Obj(
              "request" -> text,
              "text"    -> response.text,
              "toolCalls" -> ujson.Arr.from(
                response.toolCalls.map(c =>
                  ujson.Obj(
                    "id"        -> c.id,
                    "name"      -> c.name,
                    "arguments" -> ujson.read(c.arguments.render)
                  )
                )
              )
            )
            Files.writeString(exact, ujson.write(json, indent = 2)): Unit
            response
          }
        case None =>
          Future.failed(
            AssertionError(
              s"no model fixture under $directory answers request $digest; its end reads:\n" +
                text.takeRight(1200)
            )
          )

  override def stream(request: ModelRequest): Source[ModelChunk, NotUsed] = super.stream(request)

object RecordedModelProvider:

  def sha256(text: String): String =
    java.util.HexFormat
      .of()
      .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)))

  /** The request as stable text: what a fixture's `when` strings are matched against. */
  def canonical(request: ModelRequest): String =
    val b = StringBuilder()
    request.systemMessage.foreach(s => b.append("system:\n").append(s).append("\n"))
    request.tools.foreach(t => b.append("tool:").append(t.name).append("\n"))
    request.messages.foreach {
      case ChatMessage.User(content) =>
        b.append("user:\n")
        content.foreach {
          case MessageContent.Text(t) => b.append(t).append("\n")
          case other                  => b.append(other.getClass.getSimpleName).append("\n")
        }
      case ChatMessage.Assistant(text, calls) =>
        b.append("assistant:\n").append(text).append("\n")
        calls.foreach(c =>
          b.append("call:").append(c.name).append(" ").append(c.arguments.render).append("\n")
        )
      case ChatMessage.ToolResults(results) =>
        results.foreach(r =>
          b.append("tool-result:").append(r.name).append(":\n").append(r.content).append("\n")
        )
    }
    b.toString
