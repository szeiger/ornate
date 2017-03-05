package com.novocode.ornate

import java.util.Properties

import better.files._
import com.novocode.ornate.config.Global
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

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
    val res = runToStatus(args)
    if(res != 0) System.exit(res)
  }
  //#main

  // Like `main` but returns the status instead of calling `System.exit`. This is used by the sbt plugin.
  def runToStatus(args: Array[String]): Int = {
    var baseDirName: Option[String] = None
    var configFileName: Option[String] = None
    var badOption: Boolean = false
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
      1
    } else {
      val configFile = File(configFileName.get)
      val baseDir = baseDirName.map(s => File(s)).getOrElse(configFile.parent)
      val overrides = ConfigFactory.parseProperties(props).withFallback(ConfigFactory.systemProperties())
      val global = new Global(baseDir, Some(configFile), overrides)
      global.theme.build
      if(ErrorRecognitionAppender.rearm()) 1 else 0
    }
  }
}
