package voice.domain

import java.security.SecureRandom
import java.time.Instant

/**
 * The kinds of record Voice mints identifiers for, each with its prefix.
 *
 * An identifier is `<prefix>-<ULID>`: the prefix lets a link be checked for its kind before it is
 * followed (FR-029), and the ULID sorts by time and carries nothing about a person (R3).
 */
enum IdKind(val prefix: String):
  case Participant   extends IdKind("prt")
  case Conversation  extends IdKind("cnv")
  case Concern       extends IdKind("cnc")
  case Contribution  extends IdKind("ctb")
  case Issue         extends IdKind("iss")
  case PublicCopy    extends IdKind("pub")
  case PolicyOption  extends IdKind("pol")
  case Expression    extends IdKind("exp")
  case Investigation extends IdKind("inv")
  case Action        extends IdKind("act")
  case Decision      extends IdKind("dec")
  case Outcome       extends IdKind("out")
  case Development   extends IdKind("dev")
  case Proposal      extends IdKind("prp")
  case Prompt        extends IdKind("rvp")
  case Evidence      extends IdKind("evl")

object IdKind:
  def ofPrefix(prefix: String): Option[IdKind] = values.find(_.prefix == prefix)

/** Minting and checking identifiers. Never derived from anything a person typed. */
object Ids:

  private val Crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private val Shape     = "([a-z]{3})-([0-9A-HJKMNP-TV-Z]{26})".r
  private val random    = SecureRandom()

  /** A new identifier of `kind`, its time part taken from `at`. */
  def mint(kind: IdKind, at: Instant): String = s"${kind.prefix}-${ulid(at.toEpochMilli)}"

  /** The kind an identifier names, when it is one of Voice's. */
  def kindOf(id: String): Option[IdKind] = id match
    case Shape(prefix, _) => IdKind.ofPrefix(prefix)
    case _                => None

  def is(kind: IdKind, id: String): Boolean = kindOf(id).contains(kind)

  /** A 26-character ULID: 48 bits of milliseconds, then 80 random bits, in Crockford base 32. */
  def ulid(millis: Long): String =
    val bytes = new Array[Byte](10)
    random.nextBytes(bytes)
    val out = new StringBuilder(26)
    // 10 characters of time: 50 bits, of which the top 2 are zero.
    var shift = 45
    while shift >= 0 do
      out.append(Crockford(((millis >>> shift) & 31).toInt))
      shift -= 5
    // 16 characters of randomness: 80 bits.
    var acc  = BigInt(1, bytes)
    val tail = new Array[Char](16)
    var i    = 15
    while i >= 0 do
      tail(i) = Crockford((acc & 31).toInt)
      acc = acc >> 5
      i -= 1
    out.appendAll(tail).toString

  /**
   * An id that is the same each time for the same `parts`: the HMAC of them under `key`, in the
   * shape of a minted id. One participant's expression on one Issue is found again this way without
   * a lookup, and the id reveals neither part to anyone without the key.
   */
  def keyed(kind: IdKind, key: Array[Byte], parts: String*): String =
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
    var acc = BigInt(1, mac.doFinal(parts.mkString("\n").getBytes("UTF-8")).take(16))
    val out = new Array[Char](26)
    var i   = 25
    while i >= 0 do
      out(i) = Crockford((acc & 31).toInt)
      acc = acc >> 5
      i -= 1
    s"${kind.prefix}-${String(out)}"

  /** The id of an interest and of the restricted link between a participant and an issue. */
  def pair(participantId: String, otherId: String): String = s"$participantId:$otherId"
