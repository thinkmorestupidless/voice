package voice.application.erasure

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.*
import voice.domain.erasure.ErasureScope

enum RecordKind:
  case Conversation, Concern, Contribution, Expression, Interest, Notification

final case class NoteRecord(kind: RecordKind, id: String)

/**
 * Every record a participant holds, by id, keyed by the participant (FR-055). Written where each
 * record is created; read by the erasure workflow, which cannot query a view. Holds ids only.
 */
final class ParticipantRecordsEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[ErasureScope]:

  def emptyState: ErasureScope = ErasureScope()

  def note(c: NoteRecord): Effect[Done] =
    val s = currentState
    val next = c.kind match
      case RecordKind.Conversation => s.copy(conversations = (s.conversations :+ c.id).distinct)
      case RecordKind.Concern      => s.copy(concerns = (s.concerns :+ c.id).distinct)
      case RecordKind.Contribution => s.copy(contributions = (s.contributions :+ c.id).distinct)
      case RecordKind.Expression   => s.copy(expressions = (s.expressions :+ c.id).distinct)
      case RecordKind.Interest     => s.copy(interests = (s.interests :+ c.id).distinct)
      case RecordKind.Notification => s.copy(notifications = (s.notifications :+ c.id).distinct)
    if next == s then effects.reply(Done) else effects.updateState(next).thenReply(_ => Done)

  def delete: Effect[Done] = effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[ErasureScope] = effects.reply(currentState)

object ParticipantRecordsEntity
    extends KeyValueEntity.Companion[ParticipantRecordsEntity, ErasureScope](
      componentId = ComponentId("participant-records"),
      stateSerializer = Codecs.serializer[ErasureScope]("participant-records")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[NoteRecord] = Codecs.serializer[NoteRecord]("participant-records-note")

  def create(context: KeyValueEntityContext) = new ParticipantRecordsEntity(context)

  val note   = command("note")(_.note)
  val delete = command("delete")(_.delete)
  val get    = query("get")(_.get)

  /** Notes `id` for `participant`; safe to repeat. */
  def noteFor(client: ComponentClient, participant: String, kind: RecordKind, id: String): Unit =
    client.forKeyValueEntity(EntityId(participant)).call(note).invoke(NoteRecord(kind, id)): Unit
