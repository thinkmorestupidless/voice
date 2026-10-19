package voice.application.issues

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.Provenance
import voice.domain.issues.SourceCardState

final case class WriteSourceCard(
    name: String,
    licence: String,
    attribution: String,
    link: String,
    publishedAt: Option[java.time.Instant],
    provenance: Provenance
)

/**
 * A source's public card, id = its reasoning source id: what reasoning has no field for (licence,
 * attribution, link). Written after reasoning has registered the source, so it never names one
 * reasoning does not hold (R4).
 */
final class SourceCardEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[SourceCardState]:

  def emptyState: SourceCardState = SourceCardState.empty

  def write(c: WriteSourceCard): Effect[SourceCardState] =
    val _ = c.provenance
    effects
      .updateState(
        SourceCardState(
          context.entityId,
          c.name.trim,
          c.licence.trim,
          c.attribution.trim,
          c.link.trim,
          c.publishedAt
        )
      )
      .thenReply(s => s)

  def get: ReadOnlyEffect[SourceCardState] = effects.reply(currentState)

object SourceCardEntity
    extends KeyValueEntity.Companion[SourceCardEntity, SourceCardState](
      componentId = ComponentId("source-card"),
      stateSerializer = Codecs.serializer[SourceCardState]("source-card")
    ):
  given Serializer[WriteSourceCard] = Codecs.serializer[WriteSourceCard]("source-card-write")

  def create(context: KeyValueEntityContext) = new SourceCardEntity(context)

  val write = command("write")(_.write)
  val get   = query("get")(_.get)
