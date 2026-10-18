package voice.application.interests

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.Refuse
import voice.domain.*
import voice.domain.interests.*
import voice.domain.interests.NotificationEvent.*

final case class PrepareNotification(
    participantId: String,
    developmentId: String,
    issueId: String,
    text: Sealed[String],
    why: Sealed[String],
    links: Vector[String],
    provenance: Provenance
)
final case class DeliverNotification(provenance: Provenance)
final case class FailNotification(reason: String, provenance: Provenance)
final case class ReadNotification(participantId: String, provenance: Provenance)

/**
 * One update to one follower, id `participantId:developmentId` (R7). A second `prepare` addresses
 * this same entity and answers the first unchanged (`notification.duplicate`, FR-048): however
 * often a development is processed, one update exists for each follower.
 */
final class NotificationEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[NotificationRecord, NotificationEvent]:

  def emptyState: NotificationRecord =
    val _ = context
    NotificationRecord.empty

  def applyEvent(event: NotificationEvent): NotificationRecord = Notification(currentState, event)

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  def prepare(c: PrepareNotification): Effect[NotificationRecord] =
    if currentState.exists then effects.reply(currentState)
    else
      effects
        .persist(
          NotificationPrepared(
            c.participantId,
            c.developmentId,
            c.issueId,
            c.text,
            c.why,
            c.links,
            c.provenance
          )
        )
        .thenReply(s => s)

  def deliver(c: DeliverNotification): Effect[NotificationRecord] =
    if !currentState.exists || !NotificationState.canMove(
        currentState.status,
        NotificationState.Delivered
      )
    then effects.reply(currentState)
    else if currentState.status == NotificationState.Delivered then effects.reply(currentState)
    else effects.persist(NotificationDelivered(c.provenance)).thenReply(s => s)

  /**
   * A failed attempt is recorded with its reason; a delivered update is never failed afterwards.
   */
  def fail(c: FailNotification): Effect[NotificationRecord] =
    if !currentState.exists || !NotificationState.canMove(
        currentState.status,
        NotificationState.Failed
      )
    then effects.reply(currentState)
    else
      effects
        .persist(NotificationFailed(c.reason, currentState.attempts + 1, c.provenance))
        .thenReply(s => s)

  def read(c: ReadNotification): Effect[NotificationRecord] =
    Notification.checkOwner(currentState, c.participantId) match
      case Some(r)                                               => refuse(r)
      case None if currentState.status == NotificationState.Read => effects.reply(currentState)
      case None if currentState.status != NotificationState.Delivered =>
        refuse(Rules.NotificationNotOwned.refusal("There is no such update in your inbox."))
      case None => effects.persist(NotificationRead(c.provenance)).thenReply(s => s)

  /** Erasure deletes the update with its sealed text (FR-055). */
  def delete: Effect[Done] = effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[NotificationRecord] = effects.reply(currentState)

object NotificationEntity
    extends EventSourcedEntity.Companion[NotificationEntity, NotificationRecord, NotificationEvent](
      componentId = ComponentId("notification"),
      stateSerializer = Codecs.serializer[NotificationRecord]("notification"),
      eventSerializer = Codecs.serializer[NotificationEvent]("notification-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[PrepareNotification] =
    Codecs.serializer[PrepareNotification]("notification-prepare")
  given Serializer[DeliverNotification] =
    Codecs.serializer[DeliverNotification]("notification-deliver")
  given Serializer[FailNotification] = Codecs.serializer[FailNotification]("notification-fail")
  given Serializer[ReadNotification] = Codecs.serializer[ReadNotification]("notification-read")

  def create(context: EventSourcedEntityContext) = new NotificationEntity(context)

  val prepare = command("prepare")(_.prepare)
  val deliver = command("deliver")(_.deliver)
  val fail    = command("fail")(_.fail)
  val read    = command("read")(_.read)
  val delete  = command("delete")(_.delete)
  val get     = query("get")(_.get)

/** One inbox row per update; opened only for the participant's own id. */
final case class InboxRow(
    participantId: String,
    developmentId: String,
    issueId: String,
    text: Sealed[String],
    why: Sealed[String],
    links: Vector[String],
    state: String,
    at: java.time.Instant,
    attempts: Int,
    failure: Option[String]
)

final class InboxView extends View[NotificationEvent, InboxRow]:
  def onChange(event: NotificationEvent): Effect =
    def update(f: InboxRow => InboxRow) =
      rowState.fold(effects.ignore())(r => effects.updateRow(f(r)))
    event match
      case NotificationPrepared(p, d, i, text, why, links, prov) =>
        effects.updateRow(
          InboxRow(p, d, i, text, why, links, NotificationState.Prepared.toString, prov.at, 0, None)
        )
      case NotificationDelivered(prov) =>
        update(_.copy(state = NotificationState.Delivered.toString, at = prov.at))
      case NotificationFailed(reason, n, _) =>
        update(
          _.copy(state = NotificationState.Failed.toString, attempts = n, failure = Some(reason))
        )
      case NotificationRead(_) => update(_.copy(state = NotificationState.Read.toString))

object Inbox
    extends View.Companion[InboxView, NotificationEvent, InboxRow](
      componentId = ComponentId("inbox"),
      source = ChangeSource.eventsOf(NotificationEntity),
      rowSerializer = Codecs.serializer[InboxRow]("inbox-row")
    ):
  val forParticipant = query("for-participant")(
    s"SELECT payload FROM $table WHERE payload::jsonb->>'participantId' = :participant"
  )
  def create(ctx: ViewComponentContext) = new InboxView
