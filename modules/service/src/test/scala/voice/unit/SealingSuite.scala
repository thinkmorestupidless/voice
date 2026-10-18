package voice.unit

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.Codecs
import com.thinkmorestupidless.ankka.sdk.SecretStore
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, InMemorySecretStore}
import voice.domain.*
import voice.sealing.Sealing

/** V2: sealing through the secret store, in memory and in the service's own Postgres. */
abstract class SealingContract(name: String) extends munit.FunSuite:

  given JsonValueCodec[String] = Codecs.make[String]

  def store: SecretStore

  test(s"$name: a value sealed for a registered subject opens") {
    val sealing = Sealing(store)
    sealing.mintKey("prt-a")
    val value = sealing.seal("prt-a", "message", "what happened at the stop")
    assertEquals(sealing.open(value, "message"), Opened.Value("what happened at the stop"))
  }

  test(s"$name: minting twice keeps the first key") {
    val sealing = Sealing(store)
    sealing.mintKey("prt-b")
    val value = sealing.seal("prt-b", "message", "kept")
    sealing.mintKey("prt-b")
    assertEquals(sealing.open(value, "message"), Opened.Value("kept"))
  }

  test(
    s"$name: with the key destroyed every value opens as unavailable, and nothing more can be sealed"
  ) {
    val sealing = Sealing(store)
    sealing.mintKey("prt-c")
    val values = (1 to 3).map(i => sealing.seal("prt-c", "message", s"text $i"))
    sealing.destroyKey("prt-c")
    assert(sealing.openAll("prt-c", values, "message").forall(_ == Opened.Unavailable))
    val refused = intercept[Refused](sealing.seal("prt-c", "message", "after"))
    assertEquals(refused.refusal.rule, "participant.erased")
  }

  test(s"$name: erasing one subject leaves another readable") {
    val sealing = Sealing(store)
    sealing.mintKey("prt-d"); sealing.mintKey("prt-e")
    val d = sealing.seal("prt-d", "message", "d")
    val e = sealing.seal("prt-e", "message", "e")
    sealing.destroyKey("prt-d")
    assertEquals(sealing.open(d, "message"), Opened.Unavailable)
    assertEquals(sealing.open(e, "message"), Opened.Value("e"))
  }

class SealingInMemorySuite extends SealingContract("in memory"):
  val store: SecretStore = InMemorySecretStore()

class SealingPostgresSuite extends SealingContract("postgres"):
  private lazy val kit          = AnkkaTestKit.start(Seq.empty)
  def store: SecretStore        = kit.secrets
  override def afterAll(): Unit = kit.stop()

  test("postgres: a key survives a restart of the service") {
    val sealing = Sealing(store)
    sealing.mintKey("prt-f")
    val value = sealing.seal("prt-f", "message", "durable")
    kit.restartService()
    assertEquals(Sealing(kit.secrets).open(value, "message"), Opened.Value("durable"))
  }
