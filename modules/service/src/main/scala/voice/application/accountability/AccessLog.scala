package voice.application.accountability

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.Provenance

import java.time.{Instant, ZoneOffset}

/** One staff read of a restricted record (FR-005). */
final case class AccessEntry(principal: String, route: String, recordId: String, at: Instant)

final case class AccessLogState(count: Int)

enum AccessLogEvent:
  case AccessRecorded(entry: AccessEntry, provenance: Provenance)

final case class RecordAccess(entry: AccessEntry, provenance: Provenance)

/** The access log of one UTC day; one entity per day bounds its state. */
final class AccessLogEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[AccessLogState, AccessLogEvent]:

  def emptyState: AccessLogState = AccessLogState(0)

  def applyEvent(event: AccessLogEvent): AccessLogState = event match
    case AccessLogEvent.AccessRecorded(_, _) => AccessLogState(currentState.count + 1)

  def record(command: RecordAccess): Effect[Done] =
    val _ = context
    effects
      .persist(AccessLogEvent.AccessRecorded(command.entry, command.provenance))
      .thenReply(_ => Done)

object AccessLogEntity
    extends EventSourcedEntity.Companion[AccessLogEntity, AccessLogState, AccessLogEvent](
      componentId = ComponentId("access-log"),
      stateSerializer = Codecs.serializer[AccessLogState]("access-log"),
      eventSerializer = Codecs.serializer[AccessLogEvent]("access-log-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[RecordAccess] = Codecs.serializer[RecordAccess]("access-log-record")

  def create(context: EventSourcedEntityContext) = new AccessLogEntity(context)

  val record = command("record")(_.record)

  def dayOf(at: Instant): String = at.atZone(ZoneOffset.UTC).toLocalDate.toString

/** One row per access, for the operator. */
final case class AccessRow(principal: String, route: String, recordId: String, at: Instant)

final class AccessLogRowsView extends KeyedView[AccessRow]:
  def onAccess(event: AccessLogEvent, change: Change): Effect = event match
    case AccessLogEvent.AccessRecorded(e, _) =>
      effects.updateRow(
        s"${change.subject}:${e.at.toEpochMilli}:${e.principal}:${e.recordId}",
        AccessRow(e.principal, e.route, e.recordId, e.at)
      )

object AccessLogRows
    extends KeyedView.Companion[AccessLogRowsView, AccessRow](
      ComponentId("access-log-rows"),
      Codecs.serializer[AccessRow]("access-log-row")
    ):
  val accesses = source(ChangeSource.eventsOf(AccessLogEntity))(_.onAccess)

  /** Every access at or after `since` (epoch millis as text in the row's ISO time). */
  val since = query("since")(
    s"SELECT payload FROM $table WHERE payload::jsonb->>'at' >= :since ORDER BY payload::jsonb->>'at'"
  )

  def create(ctx: ViewComponentContext) = new AccessLogRowsView
