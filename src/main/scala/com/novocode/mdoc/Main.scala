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
  val processors = Seq(
    new SpecialImageProcessor(global),
    new SpecialLinkProcessor(global, site)
  )
  pages.foreach { p =>
    val pps = processors ++ p.extensions.collect { case e: Extension => e.pageProcessors(global, site) }.flatten
    pps.foreach(_.apply(p))
  }
  global.themes.foreach { name =>
    val theme = global.createTheme(name, site)
    theme.render
  }
}
