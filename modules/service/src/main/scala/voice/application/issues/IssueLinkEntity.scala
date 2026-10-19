package voice.application.issues

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.Provenance
import voice.domain.issues.IssueLinkState

final case class WriteLink(
    contributionId: String,
    participantId: String,
    issueId: String,
    provenance: Provenance
)
final case class EraseLink(provenance: Provenance)

/**
 * `pub-…` to its contribution and author: the one restricted join (R3). Erasure overwrites it with
 * the empty link before deleting it, so no stored version keeps the join (FR-055).
 */
final class IssueLinkEntity(context: KeyValueEntityContext) extends KeyValueEntity[IssueLinkState]:

  def emptyState: IssueLinkState =
    val _ = context
    IssueLinkState.empty

  def write(c: WriteLink): Effect[IssueLinkState] =
    if currentState.exists then effects.reply(currentState)
    else
      effects
        .updateState(
          IssueLinkState(c.contributionId, c.participantId, c.issueId, currentState.version + 1)
        )
        .thenReply(s => s)

  def erase(c: EraseLink): Effect[Done] =
    val _ = c
    if !currentState.exists then effects.reply(Done)
    else
      effects
        .updateState(IssueLinkState.empty.copy(version = currentState.version + 1))
        .thenReply(_ => Done)

  def delete: Effect[Done] = effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[IssueLinkState] = effects.reply(currentState)

object IssueLinkEntity
    extends KeyValueEntity.Companion[IssueLinkEntity, IssueLinkState](
      componentId = ComponentId("issue-link"),
      stateSerializer = Codecs.serializer[IssueLinkState]("issue-link")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[WriteLink] = Codecs.serializer[WriteLink]("issue-link-write")
  given Serializer[EraseLink] = Codecs.serializer[EraseLink]("issue-link-erase")

  def create(context: KeyValueEntityContext) = new IssueLinkEntity(context)

  val write  = command("write")(_.write)
  val erase  = command("erase")(_.erase)
  val delete = command("delete")(_.delete)
  val get    = query("get")(_.get)
