package com.novocode.mdoc

import better.files._
import com.novocode.mdoc.commonmark.SpecialImageProcessor

object Main extends App {
  val startDir = "doc"

  val global = new Global(file"$startDir")
  val sources = global.sourceDir.collectChildren(_.name.endsWith("md"))
  val pages = sources.map { f => PageParser.parse(global, f) }.toVector
  val toc = Toc(global, pages)
  val sip = new SpecialImageProcessor(global)
  pages.foreach { p =>
    sip(p)
    p.processors.foreach(_.apply(p))
  }
  val site = new Site(pages, toc)
  val theme = global.createTheme(site)
  theme.render
}
