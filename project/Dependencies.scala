import sbt.*

/** Every external version, written once. */
object Dependencies {

  object V {
    val scala = "3.9.0"
    // Also `runtime` in service.json: change both together.
    val ankka      = "0.12.0"
    val munit      = "1.3.6"
    val ujson      = "4.4.3"
    val postgresql = "42.7.13"
  }

  private def ankka(module: String) = "com.thinkmorestupidless" %% s"ankka-$module" % V.ankka

  val ankkaCore          = ankka("core")
  val ankkaSdk           = ankka("sdk")
  val ankkaRuntime       = ankka("runtime")
  val ankkaHttp          = ankka("http")
  val ankkaAgent         = ankka("agent")
  val ankkaAuthOidc      = ankka("auth-oidc")
  val ankkaTelemetryOtlp = ankka("telemetry-otlp")
  val ankkaTestkit       = ankka("testkit")

  val munit = "org.scalameta" %% "munit" % V.munit

  // The seed client's JSON, and reading replies in tests: neither is the service's own JSON.
  val ujson = "com.lihaoyi" %% "ujson" % V.ujson

  // For the one assertion that searches every table of the service's database for erased text.
  val postgresql = "org.postgresql" % "postgresql" % V.postgresql
}
