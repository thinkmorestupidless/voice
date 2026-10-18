package voice.sealing

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.sdk.SecretStore
import voice.domain.*

import java.util.Base64

/**
 * Seals and opens personal values under each participant's key, kept in the service's secret store
 * under `participant/<id>/key` (R1). Offered to endpoints, workflow steps, consumers, timed actions
 * and agents; never to an entity or a view, which have no secret store.
 *
 * A key is minted once, when the participant is registered, and never again: sealing for a subject
 * with no key is refused, so nothing can be sealed for a participant after their key is destroyed.
 */
final class Sealing(store: SecretStore):

  private def name(subject: String): String = s"participant/$subject/key"

  private def key(subject: String): Option[Array[Byte]] =
    store.get(name(subject)).map(Base64.getDecoder.decode)

  /** Mints the subject's key. Called once, at registration; a second call keeps the first key. */
  def mintKey(subject: String): Unit =
    if store.get(name(subject)).isEmpty then
      store.put(name(subject), Base64.getEncoder.encodeToString(SealedCipher.newKey()))

  def hasKey(subject: String): Boolean = store.get(name(subject)).isDefined

  def seal[A](subject: String, purpose: String, value: A)(using JsonValueCodec[A]): Sealed[A] =
    key(subject) match
      case Some(k) => SealedCipher.seal(k, subject, purpose, value)
      case None =>
        throw Rules.ParticipantErased.refused(
          "This participant's data has been erased.",
          "participant" -> subject
        )

  def open[A](value: Sealed[A], purpose: String)(using JsonValueCodec[A]): Opened[A] =
    value match
      case Sealed.Erased(_)           => Opened.Unavailable
      case Sealed.Present(subject, _) => SealedCipher.open(key(subject), value, purpose)

  /** Opens many values of one subject with one key read. */
  def openAll[A](subject: String, values: Seq[Sealed[A]], purpose: String)(using
      JsonValueCodec[A]
  ): Seq[Opened[A]] =
    val k = key(subject)
    values.map(v => SealedCipher.open(k, v, purpose))

  def openText(value: Sealed[String], purpose: String)(using JsonValueCodec[String]): String =
    open(value, purpose).render(identity)

  /** Destroys the subject's key: from here every value sealed for them opens as unavailable. */
  def destroyKey(subject: String): Unit = store.delete(name(subject))
