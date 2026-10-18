package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.{Principal, RequestContext}
import voice.application.erasure.*
import voice.application.participation.*
import voice.domain.*
import voice.domain.erasure.*
import voice.identity.RecoveryCredentials

import java.time.Instant

/**
 * The data-rights bodies (contracts/http-api.md, "Data rights"): classes and counts, never a value.
 */
object DataRightsJson:
  final case class ErasureBody(recoveryCredential: Option[String] = None)
  final case class VerifyBody(evidence: Option[String] = None)
  final case class HeldClass(name: String, count: Int)
  final case class DataSummary(
      held: Vector[HeldClass],
      public: Vector[String],
      retention: String,
      erasure: ErasureStatus
  )
  final case class ClassCountView(name: String, count: Int)
  final case class ReceiptView(
      cause: String,
      classes: Vector[ClassCountView],
      authorisedBy: String,
      verifiedBy: String,
      at: Instant,
      completionVerified: Boolean
  )

  /** Where an erasure is, and what it does: the same fixed words for everyone (FR-052). */
  final case class ErasureStatus(
      state: String,
      cause: Option[String],
      rule: Option[String],
      requestedAt: Option[Instant],
      erased: Vector[String],
      retained: Vector[String],
      why: String,
      remainsPublic: Vector[String]
  )
  final case class ErasureRowView(
      participant: String,
      cause: Option[String],
      state: String,
      requestedAt: Option[Instant],
      receipt: Option[ReceiptView]
  )

  given JsonValueCodec[ErasureBody]                         = Problems.codec[ErasureBody]
  given JsonValueCodec[VerifyBody]                          = Problems.codec[VerifyBody]
  given JsonValueCodec[DataSummary]                         = Problems.codec[DataSummary]
  given JsonValueCodec[ErasureStatus]                       = Problems.codec[ErasureStatus]
  given JsonValueCodec[ErasureRowView]                      = Problems.codec[ErasureRowView]
  given erasureRows: JsonValueCodec[Vector[ErasureRowView]] = Problems.codec[Vector[ErasureRowView]]

  val Retention: String =
    "Voice keeps your records while you take part. If you have no session for the dormancy period, your personal data is erased as if you had asked."

  def wire(c: ErasureCause): String = c match
    case ErasureCause.Requested => "requested"
    case ErasureCause.Dormancy  => "dormancy"

  def status(r: ErasureRecord, rule: Option[Rule] = None): ErasureStatus =
    val state = r.stage match
      case ErasureStage.NotRequested    => "None"
      case ErasureStage.Verifying       => "Verifying"
      case ErasureStage.HeldForOperator => "HeldForOperator"
      case ErasureStage.InProgress      => "InProgress"
      case ErasureStage.Completed       => "Completed"
    ErasureStatus(
      state,
      r.cause.map(wire),
      rule.map(_.name),
      r.requestedAt,
      ErasureNotice.Erased,
      ErasureNotice.Retained,
      ErasureNotice.Why,
      ErasureNotice.Public
    )

  def authority(a: Authority): String = a match
    case Authority.Participant         => "participant"
    case Authority.Operator(principal) => s"operator:$principal"
    case Authority.DormancyRule        => "dormancy-rule"

  def verification(v: Verification): String = v match
    case Verification.SessionVerified(_)             => "session"
    case Verification.HeldForOperator(_)             => "held"
    case Verification.OperatorVerified(principal, _) => s"operator:$principal"
    case Verification.NotRequired                    => "not-required"

  def receipt(r: Receipt): ReceiptView =
    ReceiptView(
      wire(r.cause),
      r.classes.map(c => ClassCountView(c.erased.wire, c.count)),
      authority(r.authorisedBy),
      verification(r.verifiedBy),
      r.at,
      r.completionVerified
    )

import DataRightsJson.*

/** What a participant may see and ask of their own data (US7), and an operator's side of it. */
final class DataRights(reach: Reach):

  private def workflow(participant: String) = reach.components.forWorkflow(EntityId(participant))

  def record(participant: String): ErasureRecord =
    workflow(participant).call(ErasureWorkflow.status).invoke()

  def summary(participant: String): DataSummary =
    val held = reach.components
      .forKeyValueEntity(EntityId(participant))
      .call(ParticipantRecordsEntity.get)
      .invoke()
    DataSummary(
      Vector(
        HeldClass("conversations", held.conversations.size),
        HeldClass("concerns", held.concerns.size),
        HeldClass("contributions", held.contributions.size),
        HeldClass("expressions", held.expressions.size),
        HeldClass("interests", held.interests.size),
        HeldClass("notifications", held.notifications.size)
      ),
      ErasureNotice.Public,
      Retention,
      status(record(participant))
    )

  /**
   * Requests erasure. A request carrying the participant's current recovery credential is verified
   * by it; any other is held for an operator (`erasure.unverified`, 202). A second request answers
   * unchanged (`erasure.already-requested`, 200).
   */
  def request(req: RequestContext, participant: String, body: ErasureBody): Reply =
    val existing = record(participant)
    if existing.started then Problems.ok(status(existing, Some(Rules.ErasureAlreadyRequested)))
    else
      val at = reach.now()
      val verification = body.recoveryCredential.map(_.trim).filter(_.nonEmpty) match
        case None => Verification.HeldForOperator(VerificationGap.NoRecoveryCredential)
        case Some(credential) =>
          val held = reach.components
            .forEventSourcedEntity(EntityId(participant))
            .call(ParticipantEntity.checkCredential)
            .invoke(CheckCredential(RecoveryCredentials.hash(credential)))
          if held then Verification.SessionVerified(at)
          else Verification.HeldForOperator(VerificationGap.CredentialDidNotMatch)
      val started = workflow(participant)
        .call(ErasureWorkflow.start)
        .invoke(
          StartErasure(
            ErasureCause.Requested,
            verification,
            Requests.provenance(req, Actor.Participant(participant), at)
          )
        )
      Problems.accepted(
        status(started, Option.when(!verification.verified)(Rules.ErasureUnverified))
      )

  // ── operator ───────────────────────────────────────────────────────────────

  def rows(state: Option[String]): Vector[ErasureRowView] =
    val view = reach.views.forView(ErasureReceipts)
    val all =
      state.map(s => view.ask(ErasureReceipts.inStage, "stage" -> s)).getOrElse(view.all()).toVector
    all
      .sortBy(_.requestedAt.map(_.toEpochMilli).getOrElse(0L))
      .map(r =>
        ErasureRowView(
          r.participantId,
          r.cause.map(wire),
          r.stage,
          r.requestedAt,
          r.receipt.map(receipt)
        )
      )

  /**
   * An operator verifies a held request; the evidence is how, and is not kept (FR-058: no free
   * text).
   */
  def verify(principal: Principal, participant: String): ErasureStatus =
    if !Ids.is(IdKind.Participant, participant) then
      throw Rules.PublicNotFound.refused("There is no such record.")
    val r = workflow(participant)
      .call(ErasureWorkflow.operatorVerify)
      .invoke(VerifyErasure(principal.subject, reach.now()))
    if !r.started then throw Rules.PublicNotFound.refused("There is no such record.")
    status(r)
