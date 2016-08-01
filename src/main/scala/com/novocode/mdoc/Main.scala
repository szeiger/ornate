package com.novocode.mdoc

import better.files._
import com.novocode.mdoc.commonmark.{ExpandTocProcessor, SpecialImageProcessor}

object Main extends App {
  val startDir = file"doc"
  val config = new GlobalConfig(startDir, startDir / "mdoc.conf")
  val global = new Global(config)
  val theme = global.createTheme

  val pages = PageParser.parseSources(global)
  val toc = TocParser.parse(config, pages)
  val site = new Site(pages, toc)

  val sip = new SpecialImageProcessor(config)
  pages.foreach { p =>
    val pagepp = p.extensions.mdoc.flatMap(_.pageProcessors(global, site))
    (sip +: pagepp).foreach(_(p))
  }

  val etp = new ExpandTocProcessor(toc)
  pages.foreach(etp)

  theme.render(site)
}
