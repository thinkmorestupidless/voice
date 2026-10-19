package voice.application.interests

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.interests.*

import java.time.Instant

final case class Follow(participantId: String, issueId: String, delivery: Delivery, at: Instant)
final case class StopFollowing(participantId: String, at: Instant)

/**
 * One participant's interest in one Issue, id `participantId:issueId` (FR-045, FR-046). Created
 * only by `follow`; following again with the same delivery answers unchanged, so the endpoint may
 * retry.
 */
final class InterestEntity(context: KeyValueEntityContext) extends KeyValueEntity[InterestRecord]:

  def emptyState: InterestRecord = InterestRecord.empty

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def follow(c: Follow): Effect[InterestRecord] =
    Interest.checkOwner(context.entityId, c.participantId) match
      case Some(r) => refuse(r)
      case None if currentState.active && currentState.delivery == c.delivery =>
        effects.reply(currentState)
      case None if currentState.active =>
        effects
          .updateState(currentState.copy(delivery = c.delivery, changedAt = c.at))
          .thenReply(s => s)
      case None =>
        effects
          .updateState(
            InterestRecord(c.participantId, c.issueId, c.delivery, c.at, active = true, c.at)
          )
          .thenReply(s => s)

  /** Stopping keeps the record, inactive: earlier updates stay, later ones are not prepared. */
  def stop(c: StopFollowing): Effect[InterestRecord] =
    Interest.checkOwner(context.entityId, c.participantId) match
      case Some(r)                      => refuse(r)
      case None if !currentState.active => effects.reply(currentState)
      case None =>
        effects.updateState(currentState.copy(active = false, changedAt = c.at)).thenReply(s => s)

  /** Erasure deletes the interest (FR-055). */
  def delete: Effect[Done] = effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[InterestRecord] = effects.reply(currentState)

object InterestEntity
    extends KeyValueEntity.Companion[InterestEntity, InterestRecord](
      componentId = ComponentId("interest"),
      stateSerializer = Codecs.serializer[InterestRecord]("interest")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[Follow]        = Codecs.serializer[Follow]("interest-follow")
  given Serializer[StopFollowing] = Codecs.serializer[StopFollowing]("interest-stop")

  def create(context: KeyValueEntityContext) = new InterestEntity(context)

  val follow = command("follow")(_.follow)
  val stop   = command("stop")(_.stop)
  val delete = command("delete")(_.delete)
  val get    = query("get")(_.get)

final case class AddFollower(participantId: String)
final case class RemoveFollower(participantId: String)

/**
 * Which participants follow one Issue, keyed by the Issue (R11): the update workflow reads it by
 * id, since only an endpoint can query a view. Adding and removing are idempotent.
 */
final class IssueFollowersEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[FollowersRecord]:

  def emptyState: FollowersRecord = FollowersRecord.empty.copy(issueId = context.entityId)

  def add(c: AddFollower): Effect[FollowersRecord] =
    if currentState.participants.contains(c.participantId) then effects.reply(currentState)
    else effects.updateState(currentState.withParticipant(c.participantId)).thenReply(s => s)

  def remove(c: RemoveFollower): Effect[FollowersRecord] =
    if !currentState.participants.contains(c.participantId) then effects.reply(currentState)
    else effects.updateState(currentState.without(c.participantId)).thenReply(s => s)

  def get: ReadOnlyEffect[FollowersRecord] = effects.reply(currentState)

object IssueFollowersEntity
    extends KeyValueEntity.Companion[IssueFollowersEntity, FollowersRecord](
      componentId = ComponentId("issue-followers"),
      stateSerializer = Codecs.serializer[FollowersRecord]("issue-followers")
    ):
  given Serializer[AddFollower]    = Codecs.serializer[AddFollower]("issue-followers-add")
  given Serializer[RemoveFollower] = Codecs.serializer[RemoveFollower]("issue-followers-remove")

  def create(context: KeyValueEntityContext) = new IssueFollowersEntity(context)

  val add    = command("add")(_.add)
  val remove = command("remove")(_.remove)
  val get    = query("get")(_.get)

/**
 * A participant's interests, for `GET /me/interests`; the row goes when the interest is deleted.
 */
final class InterestRowsView extends View[InterestRecord, InterestRecord]:
  def onChange(state: InterestRecord): Effect = effects.updateRow(state)

object InterestRows
    extends View.Companion[InterestRowsView, InterestRecord, InterestRecord](
      componentId = ComponentId("interest-rows"),
      source = ChangeSource.stateOf(InterestEntity),
      rowSerializer = Codecs.serializer[InterestRecord]("interest-row")
    ):
  val forParticipant = query("for-participant")(
    s"SELECT payload FROM $table WHERE payload::jsonb->>'participantId' = :participant"
  )
  def create(ctx: ViewComponentContext) = new InterestRowsView
