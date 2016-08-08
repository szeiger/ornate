package com.novocode.mdoc.theme

import java.net.URI

import com.novocode.mdoc._
import com.novocode.mdoc.commonmark.NodeExtensionMethods._

import better.files._
import com.novocode.mdoc.config.Global

import scala.collection.JavaConverters._

/** Base class for themes. */
abstract class Theme(global: Global) {

  /** Render the site. May create additional synthetic pages and copy resources on demand. */
  def render(site: Site): Unit

  /** Synthesize configured synthetic pages pre-TOC. Not all requested pages have to be
    * created but only the ones that are returned will be available for resolving the TOC. */
  def synthesizePages(uris: Vector[(String, URI)]): Vector[Page] = Vector.empty

  /** Get synthetic page names and the mapped URIs for pages that should be created by the theme.
    * Any pages that have to be created before resolving the TOC should be part of this. */
  def syntheticPageURIs: Vector[(String, URI)] = {
    if(global.userConfig.theme.config.hasPath("global.pages")) {
      val co = global.userConfig.theme.config.getObject("global.pages")
      co.entrySet().asScala.iterator.filter(_.getValue.unwrapped ne null).map(e =>
        (e.getKey, Util.siteRootURI.resolve(e.getValue.unwrapped.asInstanceOf[String]))
      ).toVector
    } else Vector.empty
  }
}

/** A theme that prints the document structure to stdout. */
class Dump(global: Global) extends Theme(global) {
  def render(site: Site): Unit = {
    site.pages.foreach { p =>
      println("---------- Page: "+p.uri)
      p.doc.dumpDoc()
    }
  }
}
