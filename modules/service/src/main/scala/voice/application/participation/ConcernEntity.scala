package voice.application.participation

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.*
import voice.domain.participation.*
import voice.domain.participation.ConcernEvent.*

final case class ConfirmConcern(
    participantId: String,
    conversationId: String,
    proposalId: String,
    summary: Sealed[String],
    elements: Vector[Element],
    provenance: Provenance
)
final case class OpenContribution(contributionId: String, provenance: Provenance)

/** A confirmed concern: the participant's meaning, as they confirmed it (FR-008, FR-009). */
final class ConcernEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[ConcernState, ConcernEvent]:

  def emptyState: ConcernState = ConcernState.empty

  def applyEvent(event: ConcernEvent): ConcernState = Concern(currentState, event)

  def confirm(command: ConfirmConcern): Effect[ConcernState] =
    val _ = context
    if currentState.confirmed then effects.reply(currentState)
    else
      effects
        .persist(
          ConcernConfirmed(
            command.participantId,
            command.conversationId,
            command.proposalId,
            command.summary,
            command.elements,
            command.provenance
          )
        )
        .thenReply(s => s)

  def openContribution(command: OpenContribution): Effect[ConcernState] =
    if currentState.contributions.contains(command.contributionId) then effects.reply(currentState)
    else
      effects
        .persist(ContributionOpened(command.contributionId, command.provenance))
        .thenReply(s => s)

  def delete: Effect[Done] =
    if !currentState.confirmed then effects.reply(Done)
    else effects.deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[ConcernState] = effects.reply(currentState)

object ConcernEntity
    extends EventSourcedEntity.Companion[ConcernEntity, ConcernState, ConcernEvent](
      componentId = ComponentId("concern"),
      stateSerializer = Codecs.serializer[ConcernState]("concern"),
      eventSerializer = Codecs.serializer[ConcernEvent]("concern-event")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[ConfirmConcern] = Codecs.serializer[ConfirmConcern]("concern-confirm")
  given Serializer[OpenContribution] =
    Codecs.serializer[OpenContribution]("concern-open-contribution")

  def create(context: EventSourcedEntityContext) = new ConcernEntity(context)

  val confirm          = command("confirm")(_.confirm)
  val openContribution = command("open-contribution")(_.openContribution)
  val delete           = command("delete")(_.delete)
  val get              = query("get")(_.get)

/** One row per concern, for a participant's own listing: links, no text. */
final case class ConcernRow(
    concernId: String,
    participantId: String,
    conversationId: String,
    contributions: Vector[String],
    confirmedAt: Option[java.time.Instant]
)

final class ConcernRowsView extends View[ConcernEvent, ConcernRow]:
  def onChange(event: ConcernEvent): Effect = event match
    case ConcernConfirmed(participant, conversation, _, _, _, p) =>
      effects.updateRow(
        ConcernRow(updateContext.subject, participant, conversation, Vector.empty, Some(p.at))
      )
    case ContributionOpened(id, _) =>
      rowState.fold(effects.ignore())(r =>
        effects.updateRow(r.copy(contributions = r.contributions :+ id))
      )

object ConcernRows
    extends View.Companion[ConcernRowsView, ConcernEvent, ConcernRow](
      componentId = ComponentId("concern-rows"),
      source = ChangeSource.eventsOf(ConcernEntity),
      rowSerializer = Codecs.serializer[ConcernRow]("concern-row")
    ):
  val ofParticipant = query("of-participant")(
    s"SELECT payload FROM $table WHERE payload::jsonb->>'participantId' = :participant"
  )
  def create(ctx: ViewComponentContext) = new ConcernRowsView
