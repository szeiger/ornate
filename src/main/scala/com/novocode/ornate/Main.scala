package com.novocode.ornate

import better.files._
import com.novocode.ornate.commonmark.{AttributeFencedCodeBlocksProcessor, ExpandTocProcessor, SpecialImageProcessor}
import com.novocode.ornate.config.Global

object Main extends App {
  val startDir = file"doc"
  val global = new Global(startDir, startDir / "ornate.conf")

  //#main
  val userPages = PageParser.parseSources(global)
  val themePageURIs = global.theme.syntheticPageURIs
  val themePages = global.theme.synthesizePages(themePageURIs)
  val pages = userPages ++ themePages

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

  global.theme.render(site)
  //#main
}
