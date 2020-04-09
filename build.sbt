enablePlugins(GraalVMNativeImagePlugin)

name := "notifier"

version := "0.1"

scalaVersion := "2.13.1"

graalVMNativeImageGraalVersion := Some("20.0.0-java11")

libraryDependencies ++= List(
  "org.http4s" %% "http4s-blaze-client" % "0.21.2",
  "org.http4s" %% "http4s-circe" % "0.21.2",
  "io.circe" %% "circe-generic" % "0.13.0",
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "com.monovore" %% "decline" % "1.0.0",
  "com.github.cb372" %% "scalacache-cats-effect" % "0.28.0",
  "com.github.cb372" %% "scalacache-caffeine" % "0.28.0",
  "com.github.cb372" %% "scalacache-redis" % "0.28.0",
  "com.github.cb372" %% "scalacache-circe" % "0.28.0",
  "ch.qos.logback"  %  "logback-classic" % "1.2.3",
  "io.github.jmcardon" %% "tsec-jwt-sig" % "0.2.0",
  "io.github.jmcardon" %% "tsec-jwt-mac" % "0.2.0",
  "io.circe" %% "circe-yaml" % "0.12.0",
  "com.beachape" %% "enumeratum" % "1.5.15",
  "com.beachape" %% "enumeratum-circe" % "1.5.23",
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

graalVMNativeImageOptions ++= List(
  "--initialize-at-build-time=scala.runtime.Statics$VM",
  "--report-unsupported-elements-at-runtime",
  "-H:+ReportExceptionStackTraces",
  "--no-fallback"
)
