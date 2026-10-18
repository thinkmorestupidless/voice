package voice.support

import com.thinkmorestupidless.ankka.runtime.TimerRuntime
import com.thinkmorestupidless.ankka.testkit.{MovableClock, TimerProbe}

import java.time.Instant
import scala.concurrent.duration.*

/**
 * One clock for the endpoints (R12) and the timer sweeper, so a scenario that moves time moves
 * both.
 */
final class SettableClock(start: Instant) extends voice.Clock:
  val movable: MovableClock                 = MovableClock.at(start)
  def now(): Instant                        = movable.instant()
  def set(to: Instant): Unit                = movable.moveTo(to)
  def advance(by: java.time.Duration): Unit = movable.moveTo(movable.instant().plus(by))

/** Timers for tests (V15): a short poll, a probe, and the settable clock. */
object Timers:
  def runtime(clock: SettableClock, probe: TimerProbe): TimerRuntime =
    TimerRuntime(pollInterval = 200.millis, observer = probe, clock = clock.movable)
