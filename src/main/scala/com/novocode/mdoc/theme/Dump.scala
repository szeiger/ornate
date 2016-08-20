package com.novocode.mdoc.theme

import com.novocode.mdoc.Site
import com.novocode.mdoc.config.Global
import com.novocode.mdoc.commonmark.NodeExtensionMethods._

/** A theme that prints the document structure to stdout. */
class Dump(global: Global) extends Theme(global) {
  def render(site: Site): Unit = {
    site.pages.foreach { p =>
      println("---------------------------------------- Page: "+p.uri)
      p.doc.dumpDoc()
    }
  }
}
