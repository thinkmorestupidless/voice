package voice.application.participation

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.application.erasure.{ErasureWorkflow, StartErasure}
import voice.domain.*
import voice.domain.erasure.Verification

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*

/** The participant the timer is for, and the period it was scheduled with. */
final case class DormancyCheck(participantId: String, periodMillis: Long)

/**
 * `dormancy-check` (FR-063, R19): fires a period after a participant's last session. A later
 * session means the timer it scheduled replaced this one, so finding one is `dormancy.reset`, which
 * is done; an erased or erasing participant is done too. Otherwise the erasure starts, cause
 * `Dormancy`, with no verification. Safe to repeat: the erasure workflow answers a second start
 * unchanged.
 */
final class DormancyTimedAction extends TimedAction:

  def check(c: DormancyCheck): Effect =
    val now = timerContext.clock.instant()
    val summary = timerContext.componentClient
      .forEventSourcedEntity(EntityId(c.participantId))
      .call(ParticipantEntity.summary)
      .invoke()
    val dormantSince = now.minusMillis(c.periodMillis)
    val later        = summary.lastSessionAt.exists(_.isAfter(dormantSince))
    if !summary.exists || summary.erasure != "none" || later then effects.done()
    else
      val p = Provenance(
        Actor.System("dormancy-check"),
        now,
        s"timer:${timerContext.timerName}",
        s"timer:${timerContext.timerName}"
      )
      timerContext.componentClient
        .forWorkflow(EntityId(c.participantId))
        .call(ErasureWorkflow.start)
        .invoke(StartErasure(ErasureCause.Dormancy, Verification.NotRequired, p)): Unit
      effects.done()

object DormancyTimedAction
    extends TimedAction.Companion[DormancyTimedAction](ComponentId("dormancy-check")):
  given Serializer[DormancyCheck] = Codecs.serializer[DormancyCheck]("dormancy-check")

  def create(ctx: TimedActionContext) = new DormancyTimedAction

  val check = handler[DormancyCheck]("check")(_.check)

  def timerName(participantId: String): String = s"dormancy:$participantId"

  /** Schedules, or replaces, the participant's dormancy timer for `period` from now. */
  def schedule(timers: TimerScheduler, participantId: String, period: java.time.Duration): Unit =
    val delay: FiniteDuration = period.toScala
    timers.createSingleTimer(
      timerName(participantId),
      delay,
      check.deferred(DormancyCheck(participantId, period.toMillis))
    )
