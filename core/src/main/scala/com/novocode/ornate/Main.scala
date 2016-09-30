package com.novocode.ornate

import java.util.Properties

import better.files._
import com.novocode.ornate.commonmark.{AttributeFencedCodeBlocksProcessor, ExpandTocProcessor, SpecialImageProcessor}
import com.novocode.ornate.config.Global
import com.typesafe.config.{ConfigFactory, Config}

object Main extends Logging {
  def run(global: Global): Unit = {
    //#main
    val userPages = PageParser.parseSources(global)
    val themePageURIs = global.theme.syntheticPageURIs
    val themePages = global.theme.synthesizePages(themePageURIs)
    val pages = userPages ++ themePages

    logger.info("Processing site")
    val toc = TocParser.parse(global.userConfig, pages)
    val site = new Site(pages, toc)

    val sip = new SpecialImageProcessor(global.userConfig)
    pages.foreach { p =>
      val pagepp = p.extensions.ornate.flatMap(_.pageProcessors(site))
      p.processors = (AttributeFencedCodeBlocksProcessor +: sip +: pagepp)
      p.applyProcessors()
    }

    val etp = new ExpandTocProcessor(toc)
    pages.foreach(etp)

    logger.info("Rendering site")
    global.theme.render(site)
    //#main
  }

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

  def main(args: Array[String]): Unit = {
    val res = runToStatus(args)
    if(res != 0) System.exit(res)
  }

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
      run(global)
      if(ErrorRecognitionAppender.rearm()) 1 else 0
    }
  }
}
