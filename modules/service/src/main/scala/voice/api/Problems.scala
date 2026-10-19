package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import com.thinkmorestupidless.ankka.core.CommandError
import com.thinkmorestupidless.ankka.http.{Bytes, Respond}
import voice.domain.*

/** What every route answers with: JSON under a status the route chose. */
type Reply = Respond[Bytes]

/**
 * Replies and refusals. A handler's body runs inside `handle`, which turns a refusal, thrown here
 * or carried back from an entity or workflow as a `CommandError`, into the body and status of the
 * refusals contract: `{ "rule", "status", "error", "names" }`.
 */
object Problems:

  private val Json = "application/json"

  /** The codec configuration for everything the API writes: an absent value is left out. */
  inline def codec[A]: JsonValueCodec[A] =
    JsonCodecMaker.make[A](
      CodecMakerConfig
        .withDiscriminatorFieldName(Some("type"))
        .withAllowRecursiveTypes(true)
        .withRequireDiscriminatorFirst(false)
        .withTransientEmpty(false)
        .withTransientNone(true)
        .withTransientDefault(false)
    )

  given JsonValueCodec[Refusal] = codec[Refusal]

  def json[A](status: Int, value: A, headers: Seq[(String, String)] = Seq.empty)(using
      JsonValueCodec[A]
  ): Reply =
    Respond(Bytes(Json, writeToArray(value)), status, headers.toVector)

  def ok[A](value: A)(using JsonValueCodec[A]): Reply = json(200, value)

  def created[A](value: A)(using JsonValueCodec[A]): Reply = json(201, value)

  def accepted[A](value: A)(using JsonValueCodec[A]): Reply = json(202, value)

  def noContent: Reply = Respond(Bytes(Json, Array.emptyByteArray), 204)

  def refused(refusal: Refusal): Reply = json(refusal.status, refusal)

  /** The refusal a failure carries, when it is one. */
  def refusalOf(failure: Throwable): Option[Refusal] = failure match
    case r: Refused => Some(r.refusal)
    case other =>
      CommandError.from(other).map { e =>
        Refusals
          .fromCommandError(e)
          .getOrElse(Refusal("command.refused", Refusals.statusFor(e.code), e.message))
      }

  /** Runs a route's body, answering a refusal as the refusals contract says. */
  def handle(body: => Reply): Reply =
    try body
    catch
      case failure: Throwable if refusalOf(failure).isDefined => refused(refusalOf(failure).get)
      case _: java.util.NoSuchElementException =>
        refused(Refusal(Rules.PublicNotFound.name, 404, "There is no such record."))
