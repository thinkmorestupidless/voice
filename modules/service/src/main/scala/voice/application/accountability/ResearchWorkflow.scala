package voice.application.accountability

import com.thinkmorestupidless.ankka.agent.forAgent
import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.agents.{*, given}
import voice.domain.*
import voice.domain.accountability.Suggestion

import scala.concurrent.duration.DurationInt

enum ResearchStatus:
  case NotStarted, Running, Done, Failed

final case class ResearchState(
    issueId: String,
    question: String,
    requestedBy: String,
    reasoningService: String,
    status: ResearchStatus,
    suggestions: Option[ResearchSuggestions],
    investigations: Vector[String],
    provenance: Option[Provenance]
)

final case class StartResearch(
    issueId: String,
    question: String,
    requestedBy: String,
    reasoningService: String,
    provenance: Provenance
)

/**
 * One research run, id `inv-…` minted at the request (R15): one step asks the research agent, and
 * records an investigation in `Suggested` for each it suggests, with the agent's reason and the
 * records it read. Each suggestion's id is keyed on the run, so a retried step records none twice.
 */
final class ResearchWorkflow(context: WorkflowContext) extends Workflow[ResearchState]:

  def emptyState: ResearchState =
    ResearchState("", "", "", "", ResearchStatus.NotStarted, None, Vector.empty, None)

  def start(c: StartResearch): Effect[ResearchStatus] =
    if currentState.status != ResearchStatus.NotStarted then effects.reply(currentState.status)
    else
      effects
        .updateState(
          ResearchState(
            c.issueId,
            c.question,
            c.requestedBy,
            c.reasoningService,
            ResearchStatus.Running,
            None,
            Vector.empty,
            Some(c.provenance)
          )
        )
        .transitionTo(ResearchWorkflow.research.ref)
        .thenReply(ResearchStatus.Running)

  private def provenance =
    currentState.provenance
      .getOrElse(
        Provenance(
          Actor.System("research"),
          java.time.Instant.EPOCH,
          context.workflowId,
          context.workflowId
        )
      )
      .causing(Actor.System("research"), context.workflowId)

  def researchStep: StepEffect =
    val suggestions = context.componentClient
      .forAgent(SessionId(context.workflowId))
      .call(ResearchAgent.research)
      .invoke(
        ResearchInput(currentState.issueId, currentState.question, currentState.reasoningService)
      )
    val key = context.workflowId.getBytes("UTF-8")
    val ids = suggestions.investigations.zipWithIndex.map { (s, i) =>
      val id = Ids.keyed(IdKind.Investigation, key, i.toString)
      context.componentClient
        .forEventSourcedEntity(EntityId(id))
        .call(InvestigationEntity.suggest)
        .invoke(
          SuggestInvestigation(
            currentState.issueId,
            s.question,
            Suggestion(context.workflowId, s.reason, s.records),
            provenance
          )
        ): Unit
      id
    }
    stepEffects
      .updateState(
        currentState
          .copy(status = ResearchStatus.Done, suggestions = Some(suggestions), investigations = ids)
      )
      .thenEnd

  def failedStep: StepEffect =
    stepEffects.updateState(currentState.copy(status = ResearchStatus.Failed)).thenEnd

  def status: ReadOnlyEffect[ResearchState] = effects.reply(currentState)

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(30.seconds)
      .stepTimeout(ResearchWorkflow.research, 180.seconds)
      .stepRecovery(
        ResearchWorkflow.research,
        RecoverStrategy.maxRetries(1).failoverTo(ResearchWorkflow.failed)
      )
      .build

object ResearchWorkflow
    extends Workflow.Companion[ResearchWorkflow, ResearchState](
      componentId = ComponentId("research"),
      stateSerializer = Codecs.serializer[ResearchState]("research")
    ):
  given Serializer[StartResearch]  = Codecs.serializer[StartResearch]("research-start")
  given Serializer[ResearchStatus] = Codecs.serializer[ResearchStatus]("research-status")

  def create(context: WorkflowContext) = new ResearchWorkflow(context)

  val research = step("research")(_.researchStep)
  val failed   = step("failed")(_.failedStep)

  val start  = command("start")(_.start)
  val status = query("status")(_.status)
