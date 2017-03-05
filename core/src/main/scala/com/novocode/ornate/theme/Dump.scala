package com.novocode.ornate.theme

import com.novocode.ornate.Site
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.config.Global

/** A theme that prints the document structure to stdout. */
class Dump(global: Global) extends Theme(global) {
  def render(site: Site): Unit = {
    site.pages.foreach { p =>
      println("---------------------------------------- Page: "+p.uri)
      p.doc.dumpDoc()
    }
  }
}
