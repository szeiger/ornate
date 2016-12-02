val makeDoc = TaskKey[Unit]("makeDoc")
val makeSite = TaskKey[Unit]("makeSite")

lazy val root = project.in(file("."))
  .aggregate(core, plugin)
  .dependsOn(core)
  .disablePlugins(BintrayPlugin)
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
    description := "Ornate is a tool for building multi-page HTML sites from Markdown sources.",
    bintrayReleaseOnPublish := false,
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
  )))
  .settings(
    makeDoc := (Def.taskDyn {
      val v = version.value
      val tag = if(v.endsWith("-SNAPSHOT")) "master" else "v"+v
      val args = s""" com.novocode.ornate.Main "-Dversion=$v" "-Dtag=$tag" doc/ornate.conf"""
      (runMain in Compile).toTask(args)
    }).value,
    publishArtifact := false,
    publish := {},
    publishLocal := {},
    PgpKeys.publishSigned := {},
    makeSite := {
      makeDoc.value
      val target = file("doc/target/api")
      IO.delete(target)
      IO.createDirectory(target)
      val api = (doc in Compile in core).value
      IO.copyDirectory(api, target)
    }
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
      "org.webjars.npm" % "elasticlunr" % "0.9.5",
      "org.webjars.npm" % "jquery" % "2.2.4",
      "org.webjars.npm" % "what-input" % "2.1.1",
      "org.webjars.npm" % "csso" % "2.2.1",
      "org.webjars.npm" % "mathjax" % "2.7.0",
      "com.googlecode.htmlcompressor" % "htmlcompressor" % "1.5.2",
      "rhino" % "js" % "1.7R2", // Only needed because HtmlCompressor unnecessarily references org.mozilla.javascript.ErrorReporter
      "com.google.javascript" % "closure-compiler" % "v20160911",
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
    scriptedDependencies := { val _ = ((publishLocal in core).value, publishLocal.value) },
    bintrayRepository := "sbt-plugins"
  )
