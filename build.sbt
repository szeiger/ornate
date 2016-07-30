lazy val root = (project in file(".")).enablePlugins(SbtTwirl)

organization := "com.novocode"

name := "mdoc"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-unchecked")

val commonMarkVersion = "0.5.1"

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "com.atlassian.commonmark" % "commonmark" % commonMarkVersion,
  "com.atlassian.commonmark" % "commonmark-ext-gfm-tables" % commonMarkVersion,
  "com.atlassian.commonmark" % "commonmark-ext-gfm-strikethrough" % commonMarkVersion,
  "com.atlassian.commonmark" % "commonmark-ext-autolink" % commonMarkVersion,
  "com.typesafe" % "config" % "1.3.0",
  "org.slf4j" % "slf4j-api" % "1.7.18",
  "ch.qos.logback" % "logback-classic" % "1.1.6",
  "com.github.pathikrit" %% "better-files" % "2.16.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
)
