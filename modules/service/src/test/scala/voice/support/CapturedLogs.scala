package voice.support

import ch.qos.logback.classic.spi.{ILoggingEvent, IThrowableProxy}
import ch.qos.logback.core.AppenderBase

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * Every log line of a test JVM, as text, with its throwable's messages and its MDC: what a deployed
 * service could write anywhere (FR-060). Bounded, oldest lines dropped.
 */
final class CapturedLogs extends AppenderBase[ILoggingEvent]:
  override def append(event: ILoggingEvent): Unit =
    val mdc = Option(event.getMDCPropertyMap)
      .map(_.asScala.map((k, v) => s"$k=$v").mkString(" "))
      .getOrElse("")
    CapturedLogs.add(
      s"${event.getLevel} ${event.getLoggerName} ${event.getFormattedMessage} $mdc ${CapturedLogs.throwable(event.getThrowableProxy)}"
    )

object CapturedLogs:
  private val Limit = 200_000
  private val lines = ConcurrentLinkedQueue[String]()

  private def add(line: String): Unit =
    lines.add(line)
    while lines.size > Limit do lines.poll(): Unit

  private def throwable(t: IThrowableProxy): String =
    if t == null then "" else s"${t.getClassName}: ${t.getMessage} ${throwable(t.getCause)}"

  def all: Vector[String] = lines.asScala.toVector
  def size: Int           = lines.size
