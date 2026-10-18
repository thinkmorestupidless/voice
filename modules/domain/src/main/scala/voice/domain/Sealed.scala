package voice.domain

import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}

/**
 * A personal value, sealed under the key of the participant it belongs to (R1).
 *
 * Entities and views hold it and never open it. It is `Present` while the subject's key exists in
 * the secret store and opens to the value; once the key is destroyed it opens as unavailable, and a
 * record may also be rewritten to `Erased`. It prints as `Sealed(<subject>)`, so a log or a trace
 * never shows a value.
 *
 * Serialised as `{"subject": "...", "ciphertext": "<base64>"}`, or `{"subject": "..."}` once
 * erased.
 */
enum Sealed[A]:
  case Present(subject: String, ciphertext: String)
  case Erased(subject: String)

  def subject: String

  override def toString: String = s"Sealed($subject)"

object Sealed:

  given codec[A]: JsonValueCodec[Sealed[A]] with
    def nullValue: Sealed[A] = null.asInstanceOf[Sealed[A]]

    def encodeValue(x: Sealed[A], out: JsonWriter): Unit =
      out.writeObjectStart()
      out.writeKey("subject")
      out.writeVal(x.subject)
      x match
        case Present(_, ciphertext) =>
          out.writeKey("ciphertext")
          out.writeVal(ciphertext)
        case Erased(_) => ()
      out.writeObjectEnd()

    def decodeValue(in: JsonReader, default: Sealed[A]): Sealed[A] =
      if !in.isNextToken('{') then in.decodeError("expected a sealed value")
      var subject: String    = null
      var ciphertext: String = null
      if !in.isNextToken('}') then
        in.rollbackToken()
        var more = true
        while more do
          val key = in.readKeyAsString()
          key match
            case "subject"    => subject = in.readString(null)
            case "ciphertext" => ciphertext = in.readString(null)
            case _            => in.skip()
          more = in.isNextToken(',')
        if !in.isCurrentToken('}') then in.objectEndOrCommaError()
      if subject == null then in.decodeError("a sealed value has no subject")
      if ciphertext == null then Erased(subject) else Present(subject, ciphertext)

/** What opening a sealed value gives: the value, or that it is no longer available. */
enum Opened[+A]:
  case Value(value: A)
  case Unavailable

  def toOption: Option[A] = this match
    case Value(v)    => Some(v)
    case Unavailable => None

  /** The value rendered, or the words a person sees for an erased one (FR-056). */
  def render(show: A => String): String = this match
    case Value(v)    => show(v)
    case Unavailable => Opened.Redacted

object Opened:
  val Redacted = "redacted or unavailable"

/** A sealed value whose ciphertext does not authenticate: spliced, altered or under another key. */
final class SealCorrupt(subject: String, purpose: String)
    extends RuntimeException(s"a sealed $purpose of $subject does not authenticate")

/**
 * AES-256-GCM over a value's JSON, with the subject and the purpose (the record and field the value
 * belongs to) as associated data, so a value moved from one field or subject to another is reported
 * corrupt rather than opened. Pure: the key is the caller's to fetch.
 */
object SealedCipher:

  val KeyBytes           = 32
  private val NonceBytes = 12
  private val TagBits    = 128
  private val random     = SecureRandom()
  private val b64        = Base64.getEncoder
  private val b64d       = Base64.getDecoder

  def newKey(): Array[Byte] =
    val key = new Array[Byte](KeyBytes)
    random.nextBytes(key)
    key

  private def aad(subject: String, purpose: String): Array[Byte] =
    s"voice-sealed\u0000$subject\u0000$purpose".getBytes(UTF_8)

  def seal[A](key: Array[Byte], subject: String, purpose: String, value: A)(using
      codec: JsonValueCodec[A]
  ): Sealed[A] =
    val nonce = new Array[Byte](NonceBytes)
    random.nextBytes(nonce)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TagBits, nonce))
    cipher.updateAAD(aad(subject, purpose))
    val body = cipher.doFinal(writeToArray(value))
    Sealed.Present(subject, b64.encodeToString(nonce ++ body))

  /** Opens with the subject's key; `None` for a key means the subject was erased. */
  def open[A](key: Option[Array[Byte]], value: Sealed[A], purpose: String)(using
      codec: JsonValueCodec[A]
  ): Opened[A] = (value, key) match
    case (Sealed.Erased(_), _)        => Opened.Unavailable
    case (Sealed.Present(_, _), None) => Opened.Unavailable
    case (Sealed.Present(subject, text), Some(k)) =>
      val bytes  = b64d.decode(text)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(k, "AES"),
        GCMParameterSpec(TagBits, bytes, 0, NonceBytes)
      )
      cipher.updateAAD(aad(subject, purpose))
      val plain =
        try cipher.doFinal(bytes, NonceBytes, bytes.length - NonceBytes)
        catch case _: javax.crypto.AEADBadTagException => throw SealCorrupt(subject, purpose)
      Opened.Value(readFromArray[A](plain))

/** How a field is classified (FR-059): the column in data-model.md's tables. */
enum Classification:
  /** Readable by anyone once the record is public. */
  case Public

  /** Readable by a staff role; never on a public route. */
  case Restricted

  /** Sealed under the subject's key and erased with it. */
  case Personal

  /** Personal, and shown only inside labelled aggregates. */
  case Sensitive

  /** An identifier or a link to another record; personal when it names a participant. */
  case Link

  /** A closed set of names (a state, a kind, a mode). */
  case Category
