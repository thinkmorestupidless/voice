package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.RequestContext
import voice.application.interests.*
import voice.domain.*
import voice.domain.interests.*

import java.time.Instant

object InterestsJson:
  final case class FollowBody(delivery: String = Delivery.InboxImmediate.wire)
  final case class InterestView(
      issue: String,
      title: String,
      delivery: String,
      since: Instant,
      active: Boolean
  )
  final case class InboxEntry(
      development: String,
      issue: String,
      text: String,
      links: Vector[String],
      why: String,
      state: String,
      at: Instant,
      failedAttempts: Int
  )

  given JsonValueCodec[FollowBody]                      = Problems.codec[FollowBody]
  given JsonValueCodec[InterestView]                    = Problems.codec[InterestView]
  given interests: JsonValueCodec[Vector[InterestView]] = Problems.codec[Vector[InterestView]]
  given JsonValueCodec[InboxEntry]                      = Problems.codec[InboxEntry]
  given inbox: JsonValueCodec[Vector[InboxEntry]]       = Problems.codec[Vector[InboxEntry]]

import InterestsJson.{*, given}

/**
 * Following and the inbox (FR-045 to FR-049). Following is the participant's own act on this route
 * alone: reading, searching or contributing creates no interest. The inbox opens only the
 * participant's own updates, under their key.
 */
final class Interests(reach: Reach):

  given JsonValueCodec[String] = Problems.codec[String]

  private val issues = PublicIssues(reach)

  private def interest(participant: String, issue: String) =
    reach.components.forKeyValueEntity(EntityId(Interest.id(participant, issue)))
  private def followers(issue: String) = reach.components.forKeyValueEntity(EntityId(issue))

  private def view(r: InterestRecord): InterestView =
    val title = reach.components
      .forEventSourcedEntity(EntityId(r.targetId))
      .call(voice.application.issues.IssueEntity.get)
      .invoke()
      .title
    InterestView(r.targetId, title, r.delivery.wire, r.since, r.active)

  /** Follows `issue`: the interest first, then the Issue's follower list, both safe to repeat. */
  def follow(participant: String, issue: String, body: FollowBody): InterestView =
    issues.held(issue): Unit
    val delivery = Interest.checkDelivery(body.delivery.trim).fold(r => throw Refused(r), identity)
    val at       = reach.now()
    voice.application.erasure.ParticipantRecordsEntity
      .noteFor(
        reach.components,
        participant,
        voice.application.erasure.RecordKind.Interest,
        Interest.id(participant, issue)
      )
    val saved = interest(participant, issue)
      .call(InterestEntity.follow)
      .invoke(Follow(participant, issue, delivery, at))
    followers(issue).call(IssueFollowersEntity.add).invoke(AddFollower(participant)): Unit
    view(saved)

  /** Stops following: later developments reach nothing; earlier updates stay in the inbox. */
  def stop(participant: String, issue: String): Unit =
    val held = interest(participant, issue).call(InterestEntity.get).invoke()
    if held.exists then
      interest(participant, issue)
        .call(InterestEntity.stop)
        .invoke(StopFollowing(participant, reach.now())): Unit
      followers(issue).call(IssueFollowersEntity.remove).invoke(RemoveFollower(participant)): Unit

  def list(participant: String): Vector[InterestView] =
    reach.views
      .forView(InterestRows)
      .ask(InterestRows.forParticipant, "participant" -> participant)
      .toVector
      .filter(_.active)
      .map(view)

  private def entry(r: InboxRow): InboxEntry =
    InboxEntry(
      r.developmentId,
      r.issueId,
      reach.sealing.openText(r.text, UpdatePurposes.Text),
      r.links,
      reach.sealing.openText(r.why, UpdatePurposes.Why),
      r.state.toLowerCase,
      r.at,
      r.attempts
    )

  private val Shown = Set(NotificationState.Delivered.toString, NotificationState.Read.toString)

  /**
   * The participant's delivered updates, newest last; one prepared but not delivered is not shown.
   */
  def inbox(participant: String, after: Option[Instant] = None): Vector[InboxEntry] =
    reach.views
      .forView(Inbox)
      .ask(Inbox.forParticipant, "participant" -> participant)
      .toVector
      .filter(r => Shown.contains(r.state) && after.forall(a => r.at.isAfter(a)))
      .sortBy(_.at)
      .map(entry)

  def read(request: RequestContext, participant: String, development: String): InboxEntry =
    val n =
      reach.components.forEventSourcedEntity(EntityId(Notification.id(participant, development)))
    val s = n
      .call(NotificationEntity.read)
      .invoke(
        ReadNotification(
          participant,
          Requests.provenance(request, Actor.Participant(participant), reach.now())
        )
      )
    InboxEntry(
      s.developmentId,
      s.issueId,
      reach.sealing.openText(s.text, UpdatePurposes.Text),
      s.links,
      reach.sealing.openText(s.why, UpdatePurposes.Why),
      s.status.toString.toLowerCase,
      s.deliveredAt.getOrElse(s.preparedAt),
      s.attempts
    )
