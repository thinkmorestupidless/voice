package voice.domain.erasure

import voice.domain.*

import java.time.Instant

/** A class of data an erasure removes or makes unreadable. */
enum ErasedClass(val wire: String):
  case Conversations         extends ErasedClass("conversations")
  case Concerns              extends ErasedClass("concerns")
  case Contributions         extends ErasedClass("contributions")
  case PublicCopiesWithdrawn extends ErasedClass("public-copies-withdrawn")
  case PublicCopiesRetained  extends ErasedClass("public-copies-retained")
  case Expressions           extends ErasedClass("expressions")
  case Interests             extends ErasedClass("interests")
  case Notifications         extends ErasedClass("notifications")
  case Key                   extends ErasedClass("key")
  case RecoveryCredential    extends ErasedClass("recovery-credential")

final case class ClassCount(erased: ErasedClass, count: Int)

/** Who authorised an erasure: the participant, an operator, or the dormancy rule. */
enum Authority:
  case Participant
  case Operator(principal: String)
  case DormancyRule

/**
 * The restricted audit receipt of one erasure (FR-058): the cause, the classes erased with their
 * counts, when, who authorised it and who verified it. It holds no `Sealed` value and no free text,
 * so it cannot reproduce anything erased (`erasure.receipt.personal`, checked by ReceiptSuite).
 */
final case class Receipt(
    cause: ErasureCause,
    classes: Vector[ClassCount],
    authorisedBy: Authority,
    verifiedBy: Verification,
    at: Instant,
    completionVerified: Boolean
)

object Receipt:
  def of(r: ErasureRecord, authority: Authority, at: Instant, keyGone: Boolean): Receipt =
    val s         = r.scope
    val retained  = r.retained.count(_.result == ResidueResult.Retained)
    val withdrawn = r.withdrawn.size + r.retained.count(_.result != ResidueResult.Retained)
    Receipt(
      r.cause.getOrElse(ErasureCause.Requested),
      Vector(
        ClassCount(ErasedClass.Conversations, s.conversations.size),
        ClassCount(ErasedClass.Concerns, s.concerns.size),
        ClassCount(ErasedClass.Contributions, s.contributions.size),
        ClassCount(ErasedClass.PublicCopiesWithdrawn, withdrawn),
        ClassCount(ErasedClass.PublicCopiesRetained, retained),
        ClassCount(ErasedClass.Expressions, s.expressions.size),
        ClassCount(ErasedClass.Interests, s.interests.size),
        ClassCount(ErasedClass.Notifications, s.notifications.size),
        ClassCount(ErasedClass.Key, 1),
        ClassCount(ErasedClass.RecoveryCredential, 1)
      ),
      authority,
      r.verification,
      at,
      completionVerified = keyGone
    )
