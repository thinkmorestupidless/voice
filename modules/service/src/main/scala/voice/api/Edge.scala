package voice.api

import com.thinkmorestupidless.ankka.http.{Acl, EndpointClients, Principal, RequestContext}
import voice.{Clock, Settings}
import voice.application.reasoning.ReasoningClient
import voice.domain.{Actor, Provenance}
import voice.identity.SessionTokens
import voice.sealing.Sealing

/**
 * What every endpoint is built from: the service's settings, its clock, the two ACLs' inputs, and
 * the service's timers once it has started. `dormancyOverride` lets a test shorten the dormancy
 * period for the participants it starts while it is set.
 */
final case class Edge(
    settings: Settings,
    clock: Clock,
    tokens: SessionTokens,
    staffAcl: Acl,
    timers: () => Option[com.thinkmorestupidless.ankka.sdk.TimerScheduler] = () => None,
    dormancyOverride: () => Option[java.time.Duration] = () => None
):
  def dormancyPeriod: java.time.Duration = dormancyOverride().getOrElse(settings.dormancyPeriod)

/** What an endpoint reaches through: the component and view clients, sealing and reasoning. */
final class Reach(val clients: EndpointClients, val edge: Edge):
  def components            = clients.componentClient
  def views                 = clients.viewClient
  lazy val sealing: Sealing = Sealing(clients.secrets)
  lazy val reasoning: ReasoningClient =
    ReasoningClient(clients.services, edge.settings.reasoningService)
  def now(): java.time.Instant = edge.clock.now()

  /**
   * Schedules or replaces the participant's dormancy timer: at every session start and recovery
   * (FR-063).
   */
  def resetDormancy(participantId: String): Unit =
    edge
      .timers()
      .foreach(t =>
        voice.application.participation.DormancyTimedAction
          .schedule(t, participantId, edge.dormancyPeriod)
      )

  /** Issues a concern may join, by its summary; never a decision (FR-017). */
  def suggestIssues(summary: String): Vector[voice.domain.participation.SuggestedIssue] =
    IssueSuggestions(this, summary).map(s =>
      voice.domain.participation.SuggestedIssue(s.issue, s.title, s.score)
    )

  /** A contribution's state label for its citizen (FR-042, FR-061). */
  def contributionLabel(contributionId: String): String =
    components
      .forEventSourcedEntity(com.thinkmorestupidless.ankka.core.EntityId(contributionId))
      .call(voice.application.disclosure.ContributionEntity.get)
      .invoke()
      .state
      .label

object Requests:

  /** The request's own id when the caller sent one, else a fresh one: the correlation (FR-062). */
  def correlation(request: RequestContext): String =
    request
      .header("x-request-id")
      .filter(_.nonEmpty)
      .getOrElse(java.util.UUID.randomUUID().toString)

  /** A provenance for a command the edge sends, read once at the edge (R12). */
  def provenance(request: RequestContext, actor: Actor, at: java.time.Instant): Provenance =
    val id = correlation(request)
    Provenance(actor, at, s"http:${request.method} ${request.path}", id)

  def staff(principal: Principal): Actor = Actor.Staff(principal.subject)
