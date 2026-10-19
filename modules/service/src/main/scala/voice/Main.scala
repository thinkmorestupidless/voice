package voice

import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{EndpointClients, HttpEndpoint, HttpServer}
import com.thinkmorestupidless.ankka.agent.{AgentRuntime, AnthropicProvider, ModelProvider}
import com.thinkmorestupidless.ankka.runtime.{
  Ankka,
  ClusterConfig,
  ProjectionRuntime,
  RuntimeExtension,
  TimerRuntime
}
import voice.agents.{ConcernExtractionAgent, PrivacyFlaggingAgent, ResearchAgent}
import voice.api.*
import voice.application.accountability.*
import voice.application.deliberation.*
import voice.application.disclosure.*
import voice.application.erasure.*
import voice.application.interests.*
import voice.application.issues.*
import voice.application.participation.*
import voice.identity.{SessionTokens, StaffAcl}

/**
 * The whole service. Registration is explicit, so `components` is the complete inventory of what
 * the service hosts, and `endpoints` the complete list of what it serves. A test starts the same
 * two lists under the test kit with a clock, a model, an issuer and a reasoning service of its own.
 */
object VoiceService:

  /** Every component the service hosts, and the agents' own (their session memory). */
  def components(agents: AgentRuntime): Seq[ComponentDescriptor] = own ++ agents.descriptors

  /**
   * Every component of Voice's own: entities, views, workflows, consumers, timed actions, agents.
   */
  def own: Seq[ComponentDescriptor] = Seq(
    ParticipantEntity.descriptor,
    ConversationEntity.descriptor,
    ConversationRows.descriptor,
    ConcernEntity.descriptor,
    ConcernRows.descriptor,
    ConversationTurnWorkflow.descriptor,
    ConcernExtractionAgent.descriptor,
    AccessLogEntity.descriptor,
    AccessLogRows.descriptor,
    StaffAccountEntity.descriptor,
    StaffAccountRows.descriptor,
    ContributionEntity.descriptor,
    ContributionRows.descriptor,
    DisclosureReviewWorkflow.descriptor,
    PrivacyFlaggingAgent.descriptor,
    IssueEntity.descriptor,
    IssueRows.descriptor,
    IssueLinkEntity.descriptor,
    SourceCardEntity.descriptor,
    PublicSearch.descriptor,
    PolicyOptionEntity.descriptor,
    ExpressionEntity.descriptor,
    IssueAggregates.descriptor,
    InvestigationEntity.descriptor,
    InvestigationRows.descriptor,
    ActionEntity.descriptor,
    DecisionEntity.descriptor,
    OutcomeEntity.descriptor,
    ResearchWorkflow.descriptor,
    ResearchAgent.descriptor,
    InterestEntity.descriptor,
    InterestRows.descriptor,
    IssueFollowersEntity.descriptor,
    NotificationEntity.descriptor,
    Inbox.descriptor,
    UpdateDeliveryWorkflow.descriptor,
    DevelopmentsConsumer.descriptor,
    ParticipantRecordsEntity.descriptor,
    ErasureWorkflow.descriptor,
    ErasureReceipts.descriptor,
    DormancyTimedAction.descriptor
  )

  /** Every endpoint the service serves: three, one per audience (R10). */
  def endpoints(edge: Edge): Seq[EndpointClients => HttpEndpoint] = Seq(
    clients => PublicEndpoint(Reach(clients, edge)),
    clients => CitizenEndpoint(Reach(clients, edge)),
    clients => StaffEndpoint(Reach(clients, edge))
  )

  def edge(settings: Settings, timers: TimerRuntime, clock: Clock = Clock.system): Edge =
    Edge(
      settings,
      clock,
      SessionTokens(settings.sessionKey, settings.sessionHours),
      StaffAcl.fromEnv(),
      () => Some(timers.timerScheduler)
    )

  /** The agents, on one model: Anthropic in a deployed service, a recording in a test. */
  def agents(model: ModelProvider): AgentRuntime = AgentRuntime.withDefaultModel(model)

  /** The extensions of a service, in the order they start. */
  def extensions(
      agents: AgentRuntime,
      timers: TimerRuntime,
      http: RuntimeExtension
  ): Seq[RuntimeExtension] =
    Seq(ProjectionRuntime(), timers, agents, http)

@main def run(): Unit =
  val config   = ClusterConfig.load()
  val settings = Settings.fromEnv()
  val timers   = TimerRuntime()
  val edge     = VoiceService.edge(settings, timers)
  val agents   = VoiceService.agents(AnthropicProvider.fromEnv())
  val builder  = Ankka.service.registerAll(VoiceService.components(agents))
  val service = VoiceService
    .extensions(agents, timers, HttpServer.of(VoiceService.endpoints(edge)*))
    .foldLeft(builder)(_.withExtension(_))
    .start("voice", config)
  sys.addShutdownHook(service.terminate(): Unit): Unit
  scala.concurrent.Await
    .result(service.whenTerminated, scala.concurrent.duration.Duration.Inf): Unit
