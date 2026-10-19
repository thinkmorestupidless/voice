package voice.application.reasoning

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.thinkmorestupidless.ankka.sdk.{
  ServiceClient,
  ServiceClients,
  ServiceUnanswered,
  ServiceUnresolvable
}
import voice.api.Problems
import voice.domain.{Refusal, Refused, Rules}

import java.time.Instant

/** The reasoning service's records as Voice reads them (contracts/reasoning-integration.md). */
object ReasoningRecords:
  final case class Hypothesis(id: String, statement: String)
  final case class Question(
      id: String,
      statement: String,
      hypotheses: Vector[Hypothesis],
      dated: Option[Instant] = None
  )
  final case class Withdrawal(note: String, writer: String, at: Instant)
  final case class Source(id: String, name: String)
  final case class Evidence(
      id: String,
      source: String,
      locator: Option[String] = None,
      excerpt: Option[String] = None,
      author: Option[String] = None,
      publishedAt: Option[Instant] = None,
      dated: Option[Instant] = None,
      recordedAt: Option[Instant] = None,
      withdrawal: Option[Withdrawal] = None
  )
  final case class Stance(hypothesis: String, stance: String)
  final case class Claim(
      id: String,
      holder: String,
      statement: Option[String] = None,
      derivesFrom: Vector[String] = Vector.empty,
      stances: Vector[Stance] = Vector.empty,
      revises: Option[String] = None,
      dated: Option[Instant] = None,
      recordedAt: Option[Instant] = None,
      withdrawal: Option[Withdrawal] = None
  )
  final case class ClaimRead(claim: Claim, revisedBy: Vector[String] = Vector.empty)

  final case class HypothesisRequest(id: String, statement: String)
  final case class OpenQuestion(
      id: String,
      statement: String,
      hypotheses: Vector[HypothesisRequest]
  )
  final case class RegisterHolder(id: String, kind: String, name: String)
  final case class RegisterSource(id: String, name: String)
  final case class RecordEvidence(
      source: String,
      locator: String,
      excerpt: String,
      author: Option[String] = None,
      publishedAt: Option[Instant] = None
  )
  final case class StateClaim(
      id: String,
      holder: String,
      statement: String,
      derivesFrom: Vector[String],
      stances: Vector[Stance],
      revises: Option[String] = None
  )
  final case class Withdraw(note: Option[String] = None)

  given JsonValueCodec[Question]       = Problems.codec[Question]
  given JsonValueCodec[Source]         = Problems.codec[Source]
  given JsonValueCodec[Evidence]       = Problems.codec[Evidence]
  given JsonValueCodec[ClaimRead]      = Problems.codec[ClaimRead]
  given JsonValueCodec[Claim]          = Problems.codec[Claim]
  given JsonValueCodec[OpenQuestion]   = Problems.codec[OpenQuestion]
  given JsonValueCodec[RegisterHolder] = Problems.codec[RegisterHolder]
  given JsonValueCodec[RegisterSource] = Problems.codec[RegisterSource]
  given JsonValueCodec[RecordEvidence] = Problems.codec[RecordEvidence]
  given JsonValueCodec[StateClaim]     = Problems.codec[StateClaim]
  given JsonValueCodec[Withdraw]       = Problems.codec[Withdraw]

  /** A reasoning refusal: `{rule, status, error, names}`, the same shape as Voice's. */
  final case class ReasoningRefusal(
      rule: String,
      status: Int,
      error: String,
      names: Map[String, String] = Map.empty
  )
  given JsonValueCodec[ReasoningRefusal] = Problems.codec[ReasoningRefusal]

/** The holder Voice speaks for in reasoning; the seed pack's claims are its claims. */
val VoiceHolder = "voice-evidence-pack"

/** Reasoning could not be reached, or answered with a fault: shown as "record unavailable". */
final class ReasoningUnavailable(reason: String) extends RuntimeException(reason)

/**
 * Voice's calls to the reasoning service, service to service through `services("reasoning")` (R4).
 * Writes are idempotent by id: 201 and 200 are alike. A 404 is a dangling reference, answered as
 * `None` by a read and never fabricated. `id.held-by-another` and other refusals are raised as
 * `evidence.reference.unresolved` or passed on by rule to the reviewer.
 */
final class ReasoningClient(service: => ServiceClient):
  import ReasoningRecords.{*, given}

  private def call(method: String, path: String, body: Option[Array[Byte]]) =
    try service.request(method, path, body, body.map(_ => "application/json"))
    catch
      case e: ServiceUnanswered   => throw ReasoningUnavailable(e.getMessage)
      case e: ServiceUnresolvable => throw ReasoningUnavailable(e.getMessage)

  private def read[A](path: String)(using JsonValueCodec[A]): Option[A] =
    val response = call("GET", path, None)
    response.status match
      case 200   => Some(readFromArray[A](response.body))
      case 404   => None
      case other => throw ReasoningUnavailable(s"reasoning answered $other for GET $path")

  private def write[B, A](path: String, body: B)(using JsonValueCodec[B], JsonValueCodec[A]): A =
    val response = call("POST", path, Some(writeToArray(body)))
    response.status match
      case 200 | 201 => readFromArray[A](response.body)
      case 503       => throw ReasoningUnavailable("graph tier absent")
      case status if status >= 400 && status < 500 =>
        val refusal = scala.util.Try(readFromArray[ReasoningRefusal](response.body)).toOption
        throw Refused(
          Refusal(
            refusal.map(_.rule).getOrElse(Rules.EvidenceReferenceUnresolved.name),
            status,
            refusal.map(_.error).getOrElse(s"reasoning refused POST $path"),
            refusal.map(_.names).getOrElse(Map.empty)
          )
        )
      case other => throw ReasoningUnavailable(s"reasoning answered $other for POST $path")

  private def enc(id: String) = java.net.URLEncoder.encode(id, "UTF-8")

  def question(id: String): Option[Question] = read[Question](s"/questions/${enc(id)}")
  def claim(id: String): Option[ClaimRead]   = read[ClaimRead](s"/claims/${enc(id)}")
  def evidence(id: String): Option[Evidence] = read[Evidence](s"/evidence/${enc(id)}")
  def source(id: String): Option[Source]     = read[Source](s"/sources/${enc(id)}")

  def openQuestion(q: OpenQuestion): Question = write[OpenQuestion, Question]("/questions", q)
  def registerHolder(): Unit =
    val _ = write[RegisterHolder, ujsonless.Raw](
      "/holders",
      RegisterHolder(VoiceHolder, "person", "Voice evidence pack (curated by the pilot reviewer)")
    )
  def registerSource(s: RegisterSource): Source   = write[RegisterSource, Source]("/sources", s)
  def recordEvidence(e: RecordEvidence): Evidence = write[RecordEvidence, Evidence]("/evidence", e)
  def stateClaim(c: StateClaim): Claim            = write[StateClaim, Claim]("/claims", c)
  def withdrawEvidence(id: String, note: String): Evidence =
    write[Withdraw, Evidence](s"/evidence/${enc(id)}/withdrawal", Withdraw(Some(note)))

  /** Whether a reference resolves, by kind, as a link must before it is recorded (FR-029). */
  def resolves(kind: String, id: String): Boolean = kind match
    case "question" => question(id).isDefined
    case "claim"    => claim(id).isDefined
    case "evidence" => evidence(id).isDefined
    case "source"   => source(id).isDefined
    case "hypothesis" =>
      id.split('/') match
        case Array(q, h) => question(q).exists(_.hypotheses.exists(_.id == h))
        case _           => false
    case _ => false

object ReasoningClient:
  def apply(services: => ServiceClients, name: String): ReasoningClient =
    new ReasoningClient(services(name))

/** Any JSON at all, skipped: for a reply Voice does not read. */
object ujsonless:
  final case class Raw()
  given JsonValueCodec[Raw] = new JsonValueCodec[Raw]:
    def nullValue: Raw = Raw()
    def encodeValue(x: Raw, out: JsonWriter): Unit = {
      out.writeObjectStart(); out.writeObjectEnd()
    }
    def decodeValue(in: JsonReader, default: Raw): Raw = { in.skip(); Raw() }
