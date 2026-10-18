package voice.application.interests

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.accountability.{ActionEntity, DecisionEntity, OutcomeEntity}
import voice.application.issues.IssueEntity
import voice.domain.*
import voice.domain.interests.*
import voice.domain.issues.*
import voice.sealing.Sealing
import UpdatePurposes.given

import scala.concurrent.duration.DurationInt

enum DeliveryStatus:
  case NotStarted, Running, Immaterial, Done, Failed

final case class DeliveryState(
    issueId: String,
    development: Option[Development],
    status: DeliveryStatus,
    title: String,
    followers: Vector[String],
    rendered: Option[Rendered],
    prepared: Vector[String]
)

final case class StartDelivery(issueId: String, development: Development)

object UpdatePurposes:
  val Text = "notification.text"
  val Why  = "notification.why"

  given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[String] =
    com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[String]

/**
 * One development's updates to its followers, id = the development (R7): a redelivered event
 * addresses this same workflow, and each update is a `notification` keyed on the follower and the
 * development, so a repeated step prepares nothing twice (FR-048). No model and no reviewer stands
 * between the record and the wording (FR-047).
 */
final class UpdateDeliveryWorkflow(context: WorkflowContext) extends Workflow[DeliveryState]:

  def emptyState: DeliveryState =
    DeliveryState("", None, DeliveryStatus.NotStarted, "", Vector.empty, None, Vector.empty)

  def start(c: StartDelivery): Effect[DeliveryStatus] =
    if currentState.status != DeliveryStatus.NotStarted then effects.reply(currentState.status)
    else
      effects
        .updateState(
          DeliveryState(
            c.issueId,
            Some(c.development),
            DeliveryStatus.Running,
            "",
            Vector.empty,
            None,
            Vector.empty
          )
        )
        .transitionTo(UpdateDeliveryWorkflow.resolveIssue.ref)
        .thenReply(DeliveryStatus.Running)

  private def development = currentState.development.getOrElse(sys.error("no development"))
  private def issue = context.componentClient.forEventSourcedEntity(EntityId(currentState.issueId))

  private def provenance =
    Provenance(
      Actor.System("update-delivery"),
      development.at,
      development.developmentId,
      development.developmentId
    )

  /** The Issue as it stands, read from the entity: the development must be on it. */
  def resolveIssueStep: StepEffect =
    val s = issue.call(IssueEntity.get).invoke()
    if s.development(development.developmentId).isEmpty then
      throw Rules.DevelopmentStale.refused(
        "The issue does not hold this development yet.",
        "development" -> development.developmentId
      )
    stepEffects
      .updateState(currentState.copy(title = s.title))
      .thenTransitionTo(UpdateDeliveryWorkflow.materiality.ref)

  def materialityStep: StepEffect =
    if development.materiality == Materiality.Minor then
      stepEffects.updateState(currentState.copy(status = DeliveryStatus.Immaterial)).thenEnd
    else stepEffects.thenTransitionTo(UpdateDeliveryWorkflow.followers.ref)

  /** Which participants follow the Issue, from the entity that lists them (R11). */
  def followersStep: StepEffect =
    val f = context.componentClient
      .forKeyValueEntity(EntityId(currentState.issueId))
      .call(IssueFollowersEntity.get)
      .invoke()
    stepEffects
      .updateState(currentState.copy(followers = f.participants))
      .thenTransitionTo(UpdateDeliveryWorkflow.render.ref)

  def renderStep: StepEffect =
    val d = development
    val facts = d.kind match
      case DevelopmentKind.ActionRecorded =>
        Facts.Action(
          context.componentClient
            .forEventSourcedEntity(EntityId(d.ref))
            .call(ActionEntity.get)
            .invoke()
        )
      case DevelopmentKind.DecisionRecorded =>
        Facts.Decision(
          context.componentClient
            .forEventSourcedEntity(EntityId(d.ref))
            .call(DecisionEntity.get)
            .invoke()
        )
      case DevelopmentKind.OutcomeRecorded =>
        val o = context.componentClient
          .forEventSourcedEntity(EntityId(d.ref))
          .call(OutcomeEntity.get)
          .invoke()
        val a = context.componentClient
          .forEventSourcedEntity(EntityId(o.actionId))
          .call(ActionEntity.get)
          .invoke()
        Facts.Outcome(o, Option.when(a.exists)(a))
      case DevelopmentKind.EvidenceAccepted =>
        Facts.Evidence(
          issue
            .call(IssueEntity.get)
            .invoke()
            .link(d.ref)
            .getOrElse(sys.error(s"no evidence ${d.ref}"))
        )
      case DevelopmentKind.UpdatePublished =>
        Facts.Update(
          issue
            .call(IssueEntity.get)
            .invoke()
            .updates
            .find(_.developmentId == d.ref)
            .getOrElse(sys.error(s"no update ${d.ref}"))
        )
    val rendered = Renderer.render(currentState.issueId, currentState.title, facts)
    stepEffects
      .updateState(currentState.copy(rendered = Some(rendered)))
      .thenTransitionTo(UpdateDeliveryWorkflow.prepare.ref)

  /**
   * One notification per follower whose interest, read from the entity, selects the development:
   * active, and older than it. An erased participant has no key and is passed over.
   */
  def prepareStep: StepEffect =
    val d       = development
    val r       = currentState.rendered.getOrElse(sys.error("not rendered"))
    val sealing = Sealing(context.secrets)
    val prepared = currentState.followers.filter { p =>
      val interest = context.componentClient
        .forKeyValueEntity(EntityId(Interest.id(p, currentState.issueId)))
        .call(InterestEntity.get)
        .invoke()
      if !interest.selects(d.at) || !sealing.hasKey(p) then false
      else
        voice.application.erasure.ParticipantRecordsEntity
          .noteFor(
            context.componentClient,
            p,
            voice.application.erasure.RecordKind.Notification,
            Notification.id(p, d.developmentId)
          )
        context.componentClient
          .forEventSourcedEntity(EntityId(Notification.id(p, d.developmentId)))
          .call(NotificationEntity.prepare)
          .invoke(
            PrepareNotification(
              p,
              d.developmentId,
              currentState.issueId,
              sealing.seal(p, UpdatePurposes.Text, r.text),
              sealing.seal(p, UpdatePurposes.Why, r.why),
              r.links,
              provenance
            )
          ): Unit
        true
    }
    stepEffects
      .updateState(currentState.copy(prepared = prepared))
      .thenTransitionTo(UpdateDeliveryWorkflow.deliver.ref)

  /**
   * Delivery to the inbox is the `Delivered` event; a failed attempt is recorded, and the step
   * retried.
   */
  def deliverStep: StepEffect =
    for p <- currentState.prepared do
      val n = context.componentClient.forEventSourcedEntity(
        EntityId(Notification.id(p, development.developmentId))
      )
      DeliveryFaults.failure(p) match
        case Some(reason) =>
          n.call(NotificationEntity.fail).invoke(FailNotification(reason, provenance)): Unit
          throw IllegalStateException(s"delivery failed: $reason")
        case None =>
          n.call(NotificationEntity.deliver).invoke(DeliverNotification(provenance)): Unit
    stepEffects.updateState(currentState.copy(status = DeliveryStatus.Done)).thenEnd

  def failedStep: StepEffect =
    stepEffects.updateState(currentState.copy(status = DeliveryStatus.Failed)).thenEnd

  def status: ReadOnlyEffect[DeliveryState] = effects.reply(currentState)

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(30.seconds)
      .stepTimeout(UpdateDeliveryWorkflow.deliver, 60.seconds)
      .stepRecovery(
        UpdateDeliveryWorkflow.resolveIssue,
        RecoverStrategy.maxRetries(5).failoverTo(UpdateDeliveryWorkflow.failed)
      )
      .stepRecovery(
        UpdateDeliveryWorkflow.deliver,
        RecoverStrategy.maxRetries(5).failoverTo(UpdateDeliveryWorkflow.failed)
      )
      .build

object UpdateDeliveryWorkflow
    extends Workflow.Companion[UpdateDeliveryWorkflow, DeliveryState](
      componentId = ComponentId("update-delivery"),
      stateSerializer = Codecs.serializer[DeliveryState]("update-delivery")
    ):
  given Serializer[StartDelivery]  = Codecs.serializer[StartDelivery]("update-delivery-start")
  given Serializer[DeliveryStatus] = Codecs.serializer[DeliveryStatus]("update-delivery-status")

  def create(context: WorkflowContext) = new UpdateDeliveryWorkflow(context)

  val resolveIssue = step("resolveIssue")(_.resolveIssueStep)
  val materiality  = step("materiality")(_.materialityStep)
  val followers    = step("followers")(_.followersStep)
  val render       = step("render")(_.renderStep)
  val prepare      = step("prepare")(_.prepareStep)
  val deliver      = step("deliver")(_.deliverStep)
  val failed       = step("failed")(_.failedStep)

  val start  = command("start")(_.start)
  val status = query("status")(_.status)

/**
 * Starts a development's delivery under its id, then waits for it to end before the change is done
 * with (R7). A redelivered event finds the workflow running. The wait matters after a restart: a
 * workflow the restart interrupted resumes only when it is next addressed, and an event not yet
 * done with is delivered again, which addresses it (SC-007).
 */
final class DevelopmentsConsumer(client: ComponentClient) extends Consumer[IssueEvent, Nothing]:
  def onMessage(event: IssueEvent): Effect = event match
    case IssueEvent.DevelopmentPublished(d, _) =>
      val workflow = client.forWorkflow(EntityId(d.developmentId))
      workflow
        .call(UpdateDeliveryWorkflow.start)
        .invoke(StartDelivery(messageContext.subject, d)): Unit
      try workflow.awaitEnd(UpdateDeliveryWorkflow, 60.seconds): Unit
      catch case e: CommandError if e.code == ErrorCode.WorkflowFailed => ()
      effects.done()
    case _ => effects.ignore()

object DevelopmentsConsumer
    extends Consumer.Companion[DevelopmentsConsumer, IssueEvent, Nothing](
      componentId = ComponentId("developments"),
      source = ChangeSource.eventsOf(IssueEntity)
    ):
  def create(ctx: ConsumerContext) = new DevelopmentsConsumer(ctx.componentClient)
