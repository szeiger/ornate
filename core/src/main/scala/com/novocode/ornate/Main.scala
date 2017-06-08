package com.novocode.ornate

import java.util.Properties

import better.files._
import com.novocode.ornate.config.Global
import com.typesafe.config.{ConfigFactory, Config}

object Main extends Logging {
  def printUsage: Unit = {
    println("usage: ornate [--base-dir=<path>] [-D<option>=<value>...] <config-file>")
    println("")
    println("Source, resource and target dirs in the config are relative to the base")
    println("directory. If not specified explicitly, this is the directory that contains")
    println("the config file.")
    println("")
    println("Configuration options can be overridden with -D or through system properties.")
    println("")
  }

  //#main
  def main(args: Array[String]): Unit = {
    val res = runToStatus(args)(0).asInstanceOf[Int]
    if(res != 0) System.exit(res)
  }
  //#main

  /** Like `main` but returns the status instead of calling `System.exit`. This is used by the sbt plugin.
    * The return value is an array containing the status code as a `java.lang.Integer` and the target dir
    * as a `String` (can be `null` if running fails before the target dir is known).
    */
  def runToStatus(args: Array[String]): Array[AnyRef] = {
    var baseDirName: Option[String] = None
    var configFileName: Option[String] = None
    var badOption: Boolean = false
    var targetDir: String = null
    val props = new Properties()
    args.foreach { arg =>
      if(arg.startsWith("-D") && arg.contains("=")) {
        val sep = arg.indexOf("=")
        props.setProperty(arg.substring(2, sep), arg.substring(sep+1))
      } else if(arg.startsWith("--base-dir=")) {
        baseDirName = Some(arg.substring(11))
      } else if(!arg.startsWith("-")) {
        configFileName = Some(arg)
      } else badOption = true
    }
    if(args.isEmpty || badOption || configFileName.isEmpty) {
      printUsage
      Array[AnyRef](Integer.valueOf(1), targetDir)
    } else {
      val configFile = File(configFileName.get)
      val baseDir = baseDirName.map(s => File(s)).getOrElse(configFile.parent)
      val overrides = ConfigFactory.parseProperties(props).withFallback(ConfigFactory.systemProperties())
      val global = new Global(baseDir, Some(configFile), overrides)
      targetDir = global.userConfig.targetDir.path.toString
      global.theme.build
      val status = if(ErrorRecognitionAppender.rearm()) 1 else 0
      Array[AnyRef](Integer.valueOf(status), targetDir)
    }
  }
}
