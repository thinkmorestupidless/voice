package voice.application.issues

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.Provenance
import voice.domain.issues.PolicyOptionState

final case class WriteOption(
    issueId: String,
    title: String,
    description: String,
    costs: Vector[String],
    benefits: Vector[String],
    openQuestions: Vector[String],
    provenance: Provenance
)

/** A policy option of one Issue, id `pol-…`, described without endorsement (FR-032). */
final class PolicyOptionEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[PolicyOptionState]:

  def emptyState: PolicyOptionState = PolicyOptionState.empty

  def write(c: WriteOption): Effect[PolicyOptionState] =
    def clean(xs: Vector[String]) = xs.map(_.trim).filter(_.nonEmpty)
    effects
      .updateState(
        PolicyOptionState(
          context.entityId,
          c.issueId,
          c.title.trim,
          c.description.trim,
          clean(c.costs),
          clean(c.benefits),
          clean(c.openQuestions),
          Some(c.provenance.at)
        )
      )
      .thenReply(s => s)

  def get: ReadOnlyEffect[PolicyOptionState] = effects.reply(currentState)

object PolicyOptionEntity
    extends KeyValueEntity.Companion[PolicyOptionEntity, PolicyOptionState](
      componentId = ComponentId("policy-option"),
      stateSerializer = Codecs.serializer[PolicyOptionState]("policy-option")
    ):
  given Serializer[WriteOption] = Codecs.serializer[WriteOption]("policy-option-write")

  def create(context: KeyValueEntityContext) = new PolicyOptionEntity(context)

  val write = command("write")(_.write)
  val get   = query("get")(_.get)
