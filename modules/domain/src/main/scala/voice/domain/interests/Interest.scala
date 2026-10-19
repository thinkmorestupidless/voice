package voice.domain.interests

import voice.domain.*

import java.time.Instant

/**
 * How a follower is told (FR-046). The one value in this feature; a later channel is a new value,
 * never a change to this one, so what a follower chose stays what they chose.
 */
enum Delivery(val wire: String):
  case InboxImmediate extends Delivery("inbox-immediate")

object Delivery:
  def parse(wire: String): Option[Delivery] = values.find(_.wire == wire)

/**
 * One participant following one Issue, id `participantId:issueId` (FR-045). Created only by the
 * follow command; stopping keeps the record inactive, and erasure deletes it.
 */
final case class InterestRecord(
    participantId: String,
    targetId: String,
    delivery: Delivery,
    since: Instant,
    active: Boolean,
    changedAt: Instant
):
  def exists: Boolean = participantId.nonEmpty

  /**
   * Whether this interest selects a development recorded at `at`: active, and older than it (R11).
   */
  def selects(at: Instant): Boolean = exists && active && !since.isAfter(at)

object InterestRecord:
  val empty: InterestRecord =
    InterestRecord("", "", Delivery.InboxImmediate, Instant.EPOCH, false, Instant.EPOCH)

/**
 * Which participants follow one Issue, key value entity `issue-followers` keyed by the Issue
 * (restricted). The entity answers which; each `interest` answers whether (R11).
 */
final case class FollowersRecord(issueId: String, participants: Vector[String]):
  def withParticipant(p: String): FollowersRecord =
    if participants.contains(p) then this else copy(participants = participants :+ p)
  def without(p: String): FollowersRecord = copy(participants = participants.filterNot(_ == p))

object FollowersRecord:
  val empty: FollowersRecord = FollowersRecord("", Vector.empty)

object Interest:
  def id(participantId: String, issueId: String): String = s"$participantId:$issueId"

  def checkDelivery(wire: String): Either[Refusal, Delivery] =
    Delivery
      .parse(wire)
      .toRight(
        Rules.InterestDeliveryInvalid
          .refusal(s"The only delivery is ${Delivery.InboxImmediate.wire}.")
      )

  def checkOwner(interestId: String, participantId: String): Option[Refusal] =
    Option.unless(interestId.startsWith(participantId + ":"))(
      Rules.InterestNotOwned.refusal("This interest is not yours.")
    )
