package voice.identity

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A citizen's session token: `base64url(participantId "." expiresAt) "." base64url(hmac-sha256)`
 * under `VOICE_SESSION_KEY` (R9). Verifying is pure CPU and needs no I/O, so an ACL can do it. Not
 * a JWT, and never shown to `Oidc`.
 */
final class SessionTokens(key: Array[Byte], hours: Long):

  private val enc = Base64.getUrlEncoder.withoutPadding
  private val dec = Base64.getUrlDecoder

  private def mac(bytes: Array[Byte]): Array[Byte] =
    val m = Mac.getInstance("HmacSHA256")
    m.init(SecretKeySpec(key, "HmacSHA256"))
    m.doFinal(bytes)

  /** A token for `participantId`, expiring `hours` after `now`. */
  def mint(participantId: String, now: Instant): (String, Instant) =
    val expires = now.plusSeconds(hours * 3600)
    val body    = s"$participantId.${expires.getEpochSecond}".getBytes(UTF_8)
    (s"${enc.encodeToString(body)}.${enc.encodeToString(mac(body))}", expires)

  /** The participant a token names, when it is authentic and not expired at `now`. */
  def verify(token: String, now: Instant): Option[String] =
    token.split('.') match
      case Array(b, s) =>
        scala.util
          .Try {
            val body = dec.decode(b)
            val sig  = dec.decode(s)
            if !MessageDigest.isEqual(mac(body), sig) then None
            else
              val text = String(body, UTF_8)
              val dot  = text.lastIndexOf('.')
              val id   = text.substring(0, dot)
              val exp  = text.substring(dot + 1).toLong
              if now.getEpochSecond < exp then Some(id) else None
          }
          .toOption
          .flatten
      case _ => None

/** A recovery credential: shown once, kept only as a hash (R9). */
object RecoveryCredentials:
  private val random = java.security.SecureRandom()

  def mint(): String =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)

  def hash(credential: String): String =
    java.util.HexFormat
      .of()
      .formatHex(MessageDigest.getInstance("SHA-256").digest(credential.getBytes(UTF_8)))

  def matches(credential: String, hashed: String): Boolean =
    MessageDigest.isEqual(hash(credential).getBytes(UTF_8), hashed.getBytes(UTF_8))
