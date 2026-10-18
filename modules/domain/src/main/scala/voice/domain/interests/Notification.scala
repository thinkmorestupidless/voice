package voice.domain.interests

import voice.domain.*

import java.time.Instant

/**
 * One update to one follower about one development, id `participantId:developmentId` (FR-048, R7).
 * The text and why are sealed under the follower's key. A failed delivery is recorded with its
 * reason and the attempts so far; a delivered update is delivered once.
 */
final case class NotificationRecord(
    participantId: String,
    developmentId: String,
    issueId: String,
    text: Sealed[String],
    why: Sealed[String],
    links: Vector[String],
    status: NotificationState,
    preparedAt: Instant,
    deliveredAt: Option[Instant],
    failure: Option[String],
    attempts: Int
):
  def exists: Boolean = participantId.nonEmpty

object NotificationRecord:
  val empty: NotificationRecord =
    NotificationRecord(
      "",
      "",
      "",
      Sealed.Erased(""),
      Sealed.Erased(""),
      Vector.empty,
      NotificationState.Prepared,
      Instant.EPOCH,
      None,
      None,
      0
    )

enum NotificationEvent:
  case NotificationPrepared(
      participantId: String,
      developmentId: String,
      issueId: String,
      text: Sealed[String],
      why: Sealed[String],
      links: Vector[String],
      provenance: Provenance
  )
  case NotificationDelivered(provenance: Provenance)
  case NotificationFailed(reason: String, attempts: Int, provenance: Provenance)
  case NotificationRead(provenance: Provenance)

object Notification:
  import NotificationEvent.*

  def id(participantId: String, developmentId: String): String = s"$participantId:$developmentId"

  def apply(s: NotificationRecord, e: NotificationEvent): NotificationRecord = e match
    case NotificationPrepared(p, d, i, text, why, links, prov) =>
      NotificationRecord(
        p,
        d,
        i,
        text,
        why,
        links,
        NotificationState.Prepared,
        prov.at,
        None,
        None,
        0
      )
    case NotificationDelivered(prov) =>
      s.copy(status = NotificationState.Delivered, deliveredAt = Some(prov.at))
    case NotificationFailed(reason, attempts, _) =>
      s.copy(status = NotificationState.Failed, failure = Some(reason), attempts = attempts)
    case NotificationRead(_) => s.copy(status = NotificationState.Read)

  def checkOwner(s: NotificationRecord, participantId: String): Option[Refusal] =
    Option.unless(s.exists && s.participantId == participantId)(
      Rules.NotificationNotOwned.refusal("There is no such update in your inbox.")
    )
