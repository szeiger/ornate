lazy val root = (project in file(".")).enablePlugins(SbtTwirl)

organization := "com.novocode"

name := "mdoc"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-unchecked")

testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v", "-s", "-a")

fork in Test := true

parallelExecution in Test := false

val commonMarkVersion = "0.6.0"

libraryDependencies ++= Seq(
  "com.atlassian.commonmark" % "commonmark" % commonMarkVersion,
  "com.atlassian.commonmark" % "commonmark-ext-gfm-tables" % commonMarkVersion,
  "com.atlassian.commonmark" % "commonmark-ext-gfm-strikethrough" % commonMarkVersion,
  "com.atlassian.commonmark" % "commonmark-ext-autolink" % commonMarkVersion,
  "com.typesafe" % "config" % "1.3.0",
  "org.slf4j" % "slf4j-api" % "1.7.18",
  "ch.qos.logback" % "logback-classic" % "1.1.6",
  "com.github.pathikrit" %% "better-files" % "2.16.0",
  //"org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  "org.webjars" % "webjars-locator-core" % "0.31",
  "org.webjars.npm" % "highlight.js" % "9.6.0",
  //"org.webjars.npm" % "domino" % "1.0.25",
  //"org.webjars.npm" % "mermaid" % "6.0.0",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)
