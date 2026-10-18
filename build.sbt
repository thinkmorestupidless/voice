// voice: a privacy-first civic participation service on ankka.
//
// Three modules. `domain` holds the ids, the sealed field, the refusals, the states and the rules,
// with no runtime: it cannot name a component, so the rules are tested in milliseconds. `service`
// holds every component and is the one deployable. `seed` is a client that loads the seed pack into
// the reasoning service and into Voice; it is not in the image.

import Dependencies.*

ThisBuild / scalaVersion := V.scala
ThisBuild / organization := "com.thinkmorestupidless"
// No `ThisBuild / version`: sbt-dynver derives it from the nearest tag.

// Suites that start containers contend when they overlap. Do not undo it.
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

// The service name is also the image name, the descriptor's name and the first label of the
// hostname once exposed, so it has to be one Kubernetes and DNS accept.
val serviceName = "voice"

lazy val common = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Wvalue-discard"),
  javacOptions ++= Seq("--release", "21"),
  Test / fork              := true,
  Test / parallelExecution := false,
  Test / javaOptions ++= Seq("-Xmx2g"),
  // The living features, the seed pack and the contracts are at the repository's root, and a
  // forked test JVM starts where this says.
  Test / baseDirectory        := (ThisBuild / baseDirectory).value,
  libraryDependencies += munit % Test,
  publish / skip              := true
)

lazy val domain = project
  .in(file("modules/domain"))
  .settings(common)
  .settings(
    name := "voice-domain",
    libraryDependencies ++= Seq(ankkaCore)
  )

lazy val seed = project
  .in(file("modules/seed"))
  .dependsOn(domain)
  .settings(common)
  .settings(
    name := "voice-seed",
    libraryDependencies += ujson,
    run / fork          := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value
  )

lazy val service = project
  .in(file("modules/service"))
  // The scenarios load the seed pack the way `just seed` does, so the seed is a test dependency.
  .dependsOn(domain, seed % "test->compile")
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(common)
  .settings(
    name := serviceName,
    libraryDependencies ++= Seq(
      ankkaSdk,
      ankkaRuntime,
      ankkaHttp,
      ankkaAgent,
      ankkaAuthOidc,
      // Exports traces and metrics to the collector the installation names; with none named it
      // starts nothing.
      ankkaTelemetryOtlp,
      ankkaTestkit % Test,
      ujson        % Test,
      postgresql   % Test
    ),
    Compile / mainClass := Some("voice.run"),
    run / fork          := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    // Where other services are when running locally (`ankka.local-services.reasoning`), forwarded
    // to the forked JVM that runs the service.
    run / javaOptions ++= sys.props.collect {
      case (k, v) if k.startsWith("ankka.local-services.") => s"-D$k=$v"
    }.toSeq,
    // A switch passed to sbt is set in a JVM that runs no tests; these are forwarded to the one
    // that does. A new `voice.*` test switch not listed here silently does nothing.
    Test / javaOptions ++= sys.props.collect {
      case (k, v) if k.startsWith("voice.") => s"-D$k=$v"
    }.toSeq,
    Test / envVars ++= sys.env.filter { case (k, _) =>
      k == "ANTHROPIC_API_KEY" || k == "VOICE_MODEL_RECORD"
    },
    // The live-model and tier-3 suites are run on purpose, never by `sbt test`. munit's exclude wins
    // over --include-tags, so `just live` and `just tier3` lift a tag with VOICE_INCLUDE_TAGS instead.
    Test / testOptions ++= {
      val included = sys.env.get("VOICE_INCLUDE_TAGS").toSeq.flatMap(_.split(',')).map(_.trim).toSet
      val excluded = Seq("Live", "Reasoning").filterNot(included)
      excluded
        .map(_ => Tests.Argument(TestFrameworks.MUnit, s"--exclude-tags=${excluded.mkString(",")}"))
        .take(1)
    },
    Docker / packageName      := serviceName,
    Docker / dockerRepository := sys.env.get("DOCKER_REPOSITORY"),
    // A Docker tag may not contain '+', and a dynver snapshot version does.
    Docker / version   := version.value.replace('+', '-'),
    dockerBaseImage    := "eclipse-temurin:21-jre",
    dockerUpdateLatest := true,
    dockerExposedPorts := Seq(9000),
    dockerBuildxPlatforms := sys.env
      .get("ANKKA_IMAGE_PLATFORMS")
      .toSeq
      .flatMap(_.split(','))
      .map(_.trim)
      .filter(_.nonEmpty)
  )

lazy val root = project
  .in(file("."))
  .aggregate(domain, service, seed)
  .settings(name := "voice-root", publish / skip := true)

// The platform's database schema, taken out of the ankka-runtime artifact into target/ddl, where
// docker-compose.yml mounts it as Postgres' init directory. `sbt schema` after every ankka upgrade.
lazy val schema =
  taskKey[File]("Writes ankka's database schema from the runtime artifact into target/ddl")
schema := {
  val log = streams.value.log
  val jar = (service / Compile / dependencyClasspath).value
    .map(_.data)
    .find(f => f.getName.startsWith("ankka-runtime_") && f.getName.endsWith(".jar"))
    .getOrElse(sys.error("ankka-runtime is not on the classpath"))
  val out = (ThisBuild / baseDirectory).value / "target" / "ddl"
  IO.delete(out)
  IO.createDirectory(out)
  IO.unzip(jar, out, (entry: String) => entry.startsWith("ankka/ddl/") && entry.endsWith(".sql"))
  val files = (out / "ankka" / "ddl").listFiles().toList.sortBy(_.getName)
  files.foreach(f => IO.move(f, out / f.getName))
  IO.delete(out / "ankka")
  log.info(s"schema: ${files.map(_.getName).mkString(", ")} -> $out")
  out
}
