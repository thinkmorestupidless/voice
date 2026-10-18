package voice.unit

import voice.domain.SealedCipher
import voice.identity.{RecoveryCredentials, SessionTokens}

import java.time.Instant

class SessionTokensSuite extends munit.FunSuite:

  private val tokens = SessionTokens(SealedCipher.newKey(), 12)
  private val now    = Instant.parse("2026-11-02T09:00:00Z")

  test("a token verifies to its participant until it expires") {
    val (token, expires) = tokens.mint("prt-01", now)
    assertEquals(expires, now.plusSeconds(12 * 3600))
    assertEquals(tokens.verify(token, now.plusSeconds(3600)), Some("prt-01"))
    assertEquals(tokens.verify(token, expires), None)
  }

  test("a token under another key, or altered, does not verify") {
    val (token, _) = tokens.mint("prt-01", now)
    assertEquals(SessionTokens(SealedCipher.newKey(), 12).verify(token, now), None)
    val Array(body, sig) = token.split('.')
    val forged = java.util.Base64.getUrlEncoder.withoutPadding
      .encodeToString(s"prt-02.${now.getEpochSecond + 99999}".getBytes)
    assertEquals(tokens.verify(s"$forged.$sig", now), None)
    assertEquals(tokens.verify(s"$body.x$sig", now), None)
    assertEquals(tokens.verify("not a token", now), None)
  }

  test("a recovery credential matches its hash and nothing else") {
    val credential = RecoveryCredentials.mint()
    val hash       = RecoveryCredentials.hash(credential)
    assert(RecoveryCredentials.matches(credential, hash))
    assert(!RecoveryCredentials.matches(RecoveryCredentials.mint(), hash))
    assert(!hash.contains(credential))
  }
