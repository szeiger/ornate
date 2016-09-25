package com.novocode.ornate.sbtplugin

import java.net.URLClassLoader

import sbt._
import Keys._

object OrnatePlugin extends AutoPlugin {
  object autoImport {
    //#--doc-plugin
    val ornateBaseDir     = settingKey[Option[File]]("Base directory for Ornate")
    val ornateSourceDir   = settingKey[Option[File]]("Source directory for Ornate")
    val ornateResourceDir = settingKey[Option[File]]("Resource directory for Ornate")
    val ornateTargetDir   = settingKey[Option[File]]("Target directory for the Ornate-generated site")
    val ornateConfig      = settingKey[File]("Config file for Ornate")
    val ornateSettings    = settingKey[Map[String, String]]("Extra settings for Ornate")
    val ornate = taskKey[Unit]("Run Ornate to generate the site")
    lazy val Ornate = config("ornate").hide // provides the classpath for Ornate
    //#--doc-plugin
  }
  import autoImport._

  override lazy val projectConfigurations = Seq(Ornate)

  override lazy val projectSettings = inConfig(Ornate)(Defaults.configSettings) ++ Seq(
    ornateBaseDir := Some(sourceDirectory.value),
    ornateSourceDir := ornateBaseDir.value.map(_ / "site"),
    ornateResourceDir := ornateSourceDir.value,
    ornateTargetDir := Some(target.value / "site"),
    ornateConfig := sourceDirectory.value / "ornate.conf",
    ornateSettings := Map.empty,
    ornate := ornateTask.value,
    scalaVersion in Ornate := BuildInfo.scalaVersion,
    libraryDependencies ++= Seq(
      BuildInfo.organization % (BuildInfo.name+"_2.11") % BuildInfo.version % Ornate.name,
      "org.scala-lang" % "scala-library" % BuildInfo.scalaVersion % Ornate.name
    )
  )
  lazy val ornateTask = Def.task {
    val log = streams.value.log
    log.debug("ornateSourceDir   = "+ornateSourceDir.value)
    log.debug("ornateResourceDir = "+ornateResourceDir.value)
    log.debug("ornateTargetDir   = "+ornateTargetDir.value)
    log.debug("ornateConfig      = "+ornateConfig.value)
    log.debug("classpath         = "+(dependencyClasspath in Ornate).value.files)

    val args = Seq(
      ornateBaseDir.value.map(f => "--base-dir=" + f.getAbsolutePath),
      ornateSourceDir.value.map(f => "-Dglobal.sourceDir=" + f.getAbsolutePath),
      ornateResourceDir.value.map(f => "-Dglobal.resourceDir=" + f.getAbsolutePath),
      ornateTargetDir.value.map(f => "-Dglobal.targetDir=" + f.getAbsolutePath)
    ).flatten ++ ornateSettings.value.toSeq.map { case (k, v) => s"-D$k=$v" } :+ ornateConfig.value.getAbsolutePath
    log.debug("args              = "+args)

    val parent = ClassLoader.getSystemClassLoader.getParent
    val loader = new URLClassLoader((dependencyClasspath in Ornate).value.files.map(_.toURI.toURL).toArray, parent)
    val old = Thread.currentThread.getContextClassLoader
    try {
      Thread.currentThread.setContextClassLoader(loader)
      val cl = loader.loadClass("com.novocode.ornate.Main")
      val runToStatus = cl.getMethod("runToStatus", classOf[Array[String]])
      val res = runToStatus.invoke(null, args.toArray).asInstanceOf[Int]
      if(res != 0) throw new RuntimeException("Ornate run failed with status code "+res)
    } finally {
      Thread.currentThread.setContextClassLoader(old)
      loader.close()
    }
  }
}
