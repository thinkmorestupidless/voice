package voice.unit

import java.nio.file.{Files, Path}

/**
 * The two descriptors (contracts/deployment.md): names, images and runtime as the contract says,
 * and no value in either that looks like a secret or an installation's hostname. Secrets arrive by
 * `secretKeyRef`; installation values arrive as `${…}` placeholders filled at deploy time.
 */
class DescriptorSuite extends munit.FunSuite:

  private def read(path: String): ujson.Value = ujson.read(Files.readString(Path.of(path)))

  private val voice = read("service.json")
  private val web   = read("web/service.json")

  private val Placeholder = """^\$\{[A-Z_]+\}$""".r

  /**
   * An environment variable's or a secret key's name: upper case and underscores, never a value.
   */
  private val VariableName = """^[A-Z][A-Z0-9_]+$""".r
  private val Hostname     = """(?i)(https?://|[a-z0-9-]+\.[a-z0-9-]+\.[a-z]{2,})""".r
  private val SecretLike   = """^[A-Za-z0-9+/=_-]{24,}$""".r
  private val SecretName   = """(?i).*(KEY|SECRET|TOKEN|PASSWORD|CODES).*""".r

  /** Every string in a descriptor, at any depth. */
  private def strings(j: ujson.Value): Seq[String] = j match
    case ujson.Str(s) => Seq(s)
    case ujson.Obj(m) => m.values.toSeq.flatMap(strings)
    case ujson.Arr(a) => a.toSeq.flatMap(strings)
    case _            => Seq.empty

  test("the service descriptor names the image, the runtime and every setting the service reads") {
    assertEquals(voice("name").str, "voice")
    assertEquals(voice("service")("image").str, "voice:${VERSION}")
    assertEquals(voice("service")("runtime").str, "0.12.0")
    val names = voice("service")("env").arr.map(_("name").str).toSet
    for wanted <- Seq(
        "VOICE_SESSION_KEY",
        "VOICE_INVITE_CODES",
        "ANTHROPIC_API_KEY",
        "VOICE_DORMANCY_PERIOD",
        "VOICE_REASONING_SERVICE",
        "ANKKA_AUTH_ISSUERS"
      )
    do assert(names.contains(wanted), s"$wanted is not set")
  }

  test("every secret arrives by reference to voice-secrets, never as a value") {
    for e <- voice("service")("env").arr do
      val name = e("name").str
      if SecretName.matches(name) then
        assert(!e.obj.contains("value"), s"$name has a value")
        assertEquals(e("secretKeyRef")("name").str, "voice-secrets", name)
        assertEquals(e("secretKeyRef")("key").str, name)
  }

  test("no value in either descriptor looks like a secret or a hostname") {
    for
      (file, j) <- Seq("service.json" -> voice, "web/service.json" -> web); s <- strings(j)
      if !Placeholder.matches(s) && !VariableName.matches(s)
    do
      assert(Hostname.findFirstIn(s).isEmpty, s"$file holds what looks like a hostname: $s")
      assert(!SecretLike.matches(s), s"$file holds what looks like a secret: $s")
  }

  test("the web descriptor mounts the service at /api and exposes nothing else") {
    assertEquals(web("name").str, "voice-web")
    assertEquals(web("service")("hosting").str, "web")
    assertEquals(web("service")("processPort").num.toInt, 3000)
    assertEquals(
      web("service")("mounts").arr.map(m => (m("path").str, m("service").str)).toSeq,
      Seq("/api" -> "voice")
    )
  }
