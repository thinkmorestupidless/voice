package voice.domain.erasure

import voice.domain.*

import java.time.Instant

/**
 * How a requested erasure was verified (FR-053). A dormancy erasure has no requester and needs no
 * verification; a request that cannot be verified from the session is held for an operator.
 */
enum Verification:
  case SessionVerified(at: Instant)
  case HeldForOperator(reason: VerificationGap)
  case OperatorVerified(principal: String, at: Instant)
  case NotRequired

  def verified: Boolean = this match
    case HeldForOperator(_) => false
    case _                  => true

/** Why a request is held: a category, never the participant's words. */
enum VerificationGap:
  case NoRecoveryCredential, CredentialDidNotMatch

/** Where an erasure is, as the participant and the operator are told (contracts/http-api.md). */
enum ErasureStage(val wire: String):
  case NotRequested    extends ErasureStage("none")
  case Verifying       extends ErasureStage("verifying")
  case HeldForOperator extends ErasureStage("held-for-operator")
  case InProgress      extends ErasureStage("in-progress")
  case Completed       extends ErasureStage("completed")

/** Every record the participant holds, found by id from their own index (FR-055). */
final case class ErasureScope(
    conversations: Vector[String] = Vector.empty,
    concerns: Vector[String] = Vector.empty,
    contributions: Vector[String] = Vector.empty,
    expressions: Vector[String] = Vector.empty,
    interests: Vector[String] = Vector.empty,
    notifications: Vector[String] = Vector.empty
):
  def ++(o: ErasureScope): ErasureScope =
    ErasureScope(
      (conversations ++ o.conversations).distinct,
      (concerns ++ o.concerns).distinct,
      (contributions ++ o.contributions).distinct,
      (expressions ++ o.expressions).distinct,
      (interests ++ o.interests).distinct,
      (notifications ++ o.notifications).distinct
    )

/** What the residue check decided for one retained public copy (FR-057). */
enum ResidueResult:
  case Retained
  case Suppressed(reason: ResidueReason)

enum ResidueReason:
  /**
   * The copy was flagged at review for a distinctive circumstance, health, a minor or a small
   * group.
   */
  case DistinctiveCombination

/** One public copy of the participant's: where it is and what became of it. No text. */
final case class RetainedCopy(pub: String, issueId: String, result: ResidueResult)

/** One step of the erasure, done. */
final case class StepDone(step: ErasureStep, at: Instant)

enum ErasureStep:
  case Verify, Inventory, Revoke, Withdraw, Residue, DestroyKey, PurgeDerived, Complete

/**
 * One participant's erasure, the `erasure` workflow's state (R1). Restricted: the operator reads it
 * through the receipts view; the participant is told its stage.
 */
final case class ErasureRecord(
    participantId: String,
    cause: Option[ErasureCause],
    verification: Verification,
    stage: ErasureStage,
    requestedAt: Option[Instant],
    scope: ErasureScope,
    withdrawn: Vector[String],
    retained: Vector[RetainedCopy],
    steps: Vector[StepDone],
    receipt: Option[Receipt]
):
  def started: Boolean                 = stage != ErasureStage.NotRequested
  def done(step: ErasureStep): Boolean = steps.exists(_.step == step)
  def mark(step: ErasureStep, at: Instant): ErasureRecord =
    if done(step) then this else copy(steps = steps :+ StepDone(step, at))

object ErasureRecord:
  val empty: ErasureRecord =
    ErasureRecord(
      "",
      None,
      Verification.NotRequired,
      ErasureStage.NotRequested,
      None,
      ErasureScope(),
      Vector.empty,
      Vector.empty,
      Vector.empty,
      None
    )

/** What the participant is told before and during an erasure (FR-052): fixed words, by class. */
object ErasureNotice:
  val Erased: Vector[String] = Vector(
    "your private conversations and the concerns you confirmed",
    "your drafts and contributions that were never published",
    "the priorities, trade-offs and considerations you expressed",
    "the issues you follow and the updates in your inbox",
    "your key, so nothing sealed for you can be read again",
    "your recovery credential, so this session cannot be recovered"
  )
  val Retained: Vector[String] = Vector(
    "a de-identified contribution you published stays on its issue, with no link to you, marked as from an author who asked to be erased",
    "the issue's counts, which say how many authors were erased and why",
    "a receipt for the operator, recording which classes of data were erased and when, with none of their content"
  )
  val Why: String =
    "A published de-identified contribution became part of a public issue others rely on; it holds nothing that identifies you, and a reviewer checked it before it was published. A contribution flagged at review as possibly identifying is withdrawn instead."
  val Public: Vector[String] = Vector(
    "de-identified contributions you published, without your name or any link to you",
    "contributions you published under your name or for representatives only are withdrawn"
  )
