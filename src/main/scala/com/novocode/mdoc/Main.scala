package com.novocode.mdoc

import better.files._
import com.novocode.mdoc.commonmark.{SpecialLinkProcessor, SpecialImageProcessor}

object Main extends App {
  val startDir = "doc"

  val global = new Global(file"$startDir")
  val sources = global.sourceDir.collectChildren(_.name.endsWith("md"))
  val pages = sources.map { f => PageParser.parse(global, f) }.toVector
  val toc = Toc(global, pages)
  val site = new Site(pages, toc)
  val sip = new SpecialImageProcessor(global)
  val slp = new SpecialLinkProcessor(global, site)
  val theme = global.createTheme(site)
  val themepp = theme.pageProcessors(global, site)
  pages.foreach { p =>
    val pagepp = p.extensions.collect { case e: Extension => e.pageProcessors(global, site) }.flatten
    val pp = (sip +: pagepp ++: themepp) :+ slp
    pp.foreach(_.apply(p))
  }
  theme.render
}
