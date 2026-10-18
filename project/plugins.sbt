// Packaging the service as a container image (`sbt service/Docker/publishLocal`).
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"        % "2.6.2")
// The version comes from the nearest git tag, never from a file: only a tag publishes.
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
