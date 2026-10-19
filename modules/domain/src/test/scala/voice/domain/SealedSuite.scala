package voice.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.Codecs

final case class Holder(note: Sealed[String], maybe: Option[Sealed[String]])

/**
 * V1: a sealed value round-trips through the platform's serializer and has the envelope's shape.
 */
class SealedSuite extends munit.FunSuite:

  given JsonValueCodec[String] = Codecs.make[String]

  private val key     = SealedCipher.newKey()
  private val subject = "prt-01JABCDEFGHJKMNPQRSTVWXYZ0"

  test("seals and opens with the subject's key") {
    val sealedValue = SealedCipher.seal(key, subject, "message.text", "the bus stopped in May")
    assertEquals(
      SealedCipher.open(Some(key), sealedValue, "message.text"),
      Opened.Value("the bus stopped in May")
    )
  }

  test("round-trips through Codecs.serializer, as an entity's state would") {
    val serializer = Codecs.serializer[Holder]("holder")
    val holder     = Holder(SealedCipher.seal(key, subject, "a", "x"), Some(Sealed.Erased(subject)))
    assertEquals(serializer.fromBytes(serializer.toBytes(holder)), holder)
  }

  test("the envelope is the subject and the ciphertext, and nothing else") {
    val serializer = Codecs.serializer[Holder]("holder")
    val json = String(
      serializer.toBytes(Holder(SealedCipher.seal(key, subject, "a", "plain words"), None)),
      "UTF-8"
    )
    assert(json.contains(s""""subject":"$subject""""), json)
    assert(json.contains(""""ciphertext":""""), json)
    assert(!json.contains("plain words"), json)
  }

  test("an erased value serialises with its subject alone") {
    val serializer = Codecs.serializer[Holder]("holder")
    val json       = String(serializer.toBytes(Holder(Sealed.Erased(subject), None)), "UTF-8")
    assert(json.contains(s"""{"subject":"$subject"}"""), json)
  }

  test("with the key gone, a value opens as unavailable and renders as the spec's words") {
    val sealedValue = SealedCipher.seal(key, subject, "a", "secret")
    val opened      = SealedCipher.open[String](None, sealedValue, "a")
    assertEquals(opened, Opened.Unavailable)
    assertEquals(opened.render(identity), "redacted or unavailable")
  }

  test("a value moved to another field or under another subject is corrupt, not erased") {
    val sealedValue = SealedCipher.seal(key, subject, "a", "secret")
    intercept[SealCorrupt](SealedCipher.open(Some(key), sealedValue, "b"))
    val moved = Sealed
      .Present[String]("prt-other", sealedValue.asInstanceOf[Sealed.Present[String]].ciphertext)
    intercept[SealCorrupt](SealedCipher.open(Some(key), moved, "a"))
  }

  test("a sealed value prints its subject and never its value") {
    val sealedValue = SealedCipher.seal(key, subject, "a", "secret")
    assertEquals(sealedValue.toString, s"Sealed($subject)")
  }
