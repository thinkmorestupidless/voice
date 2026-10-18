package voice.application.interests

import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, TimeUnit}

/**
 * A seam for the delivery scenarios, inert unless a test arms it: a number of failures to report
 * for one participant's next deliveries, or a hold that keeps the delivery step waiting so a test
 * can restart the service between preparation and delivery (SC-007). Nothing in the service arms
 * it.
 */
object DeliveryFaults:
  private val failures = ConcurrentHashMap[String, Integer]()
  private val holds    = ConcurrentHashMap[String, CountDownLatch]()

  def failNext(participantId: String, times: Int): Unit = failures.put(participantId, times): Unit

  def hold(participantId: String): Unit = holds.put(participantId, CountDownLatch(1)): Unit

  def release(participantId: String): Unit =
    Option(holds.remove(participantId)).foreach(_.countDown())

  def clear(): Unit =
    failures.clear()
    holds.values.forEach(_.countDown())
    holds.clear()

  /** The reason a delivery to the participant fails now, if one was armed. */
  private[interests] def failure(participantId: String): Option[String] =
    Option(holds.get(participantId)).foreach(_.await(20, TimeUnit.SECONDS): Unit)
    val left = Option(failures.get(participantId)).map(_.intValue).getOrElse(0)
    if left <= 0 then None
    else
      failures.put(participantId, left - 1)
      Some("inbox unavailable")
