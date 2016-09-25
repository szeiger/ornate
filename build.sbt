lazy val root = project.in(file("."))
  .aggregate(core, plugin)
  .dependsOn(core)
  .settings(inThisBuild(Seq(
    organization := "com.novocode",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.11.8",
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    homepage := Some(url("https://szeiger.github.io/ornate-doc/")),
    scmInfo := Some(ScmInfo(url("https://github.com/szeiger/ornate"), "git@github.com:szeiger/ornate.git")),
    developers := List(
      Developer("szeiger", "Stefan Zeiger", "szeiger@novocode.com", url("https://github.com/szeiger"))
    ),
    description := "Ornate is a tool for building multi-page HTML sites from Markdown sources."
  )))
  .settings(
    TaskKey[Unit]("makeDoc") := (Def.taskDyn {
      val args = s""" com.novocode.ornate.Main "-Dversion=${version.value}" doc/ornate.conf"""
      (runMain in Compile).toTask(args)
    }).value
  )

val commonMarkVersion = "0.6.0"

lazy val core = project.in(file("core"))
  .enablePlugins(SbtTwirl)
  .settings(
    name := "ornate",
    libraryDependencies ++= Seq(
      "com.atlassian.commonmark" % "commonmark" % commonMarkVersion,
      "com.atlassian.commonmark" % "commonmark-ext-gfm-tables" % commonMarkVersion,
      "com.atlassian.commonmark" % "commonmark-ext-gfm-strikethrough" % commonMarkVersion,
      "com.atlassian.commonmark" % "commonmark-ext-autolink" % commonMarkVersion,
      "com.typesafe" % "config" % "1.3.0",
      "org.slf4j" % "slf4j-api" % "1.7.18",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
      "com.github.pathikrit" %% "better-files" % "2.16.0",
      "com.typesafe.play" %% "play-json" % "2.5.5",
      "org.webjars" % "webjars-locator-core" % "0.31",
      "org.webjars.npm" % "highlight.js" % "9.6.0",
      "org.webjars.npm" % "emojione" % "2.2.6",
      "ch.qos.logback" % "logback-classic" % "1.1.6",
      "com.novocode" % "junit-interface" % "0.11" % "test"
    ),
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v", "-s", "-a"),
    fork in Test := true,
    parallelExecution in Test := false
  )

lazy val plugin = project.in(file("plugin"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "sbt-ornate",
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    buildInfoKeys := Seq[BuildInfoKey](organization, (name in core), version, (scalaVersion in core)),
    buildInfoPackage := "com.novocode.ornate.sbtplugin",
    scriptedSettings,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false,
    scriptedDependencies := { val _ = ((publishLocal in core).value, publishLocal.value) }
  )
