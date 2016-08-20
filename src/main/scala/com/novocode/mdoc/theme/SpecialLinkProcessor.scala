package com.novocode.mdoc.theme

import java.net.URI

import com.novocode.mdoc.commonmark.PageProcessor
import com.novocode.mdoc.{Util, Page, Site}
import org.commonmark.node.{Image, Link, AbstractVisitor}

/** Resolve links and image targets to the proper destination. */
class SpecialLinkProcessor(imageResources: Resources, site: Site, suffix: String, resourcePaths: Set[String]) extends PageProcessor {
  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Link): Unit = {
        n.setDestination(resolve(p.uri, n.getDestination, "link", true, false))
        super.visit(n)
      }
      override def visit(n: Image): Unit = {
        n.setDestination(resolve(p.uri, n.getDestination, "image", false, true))
        super.visit(n)
      }
    })
  }

  def resolve(pageURI: URI, destination: String, tpe: String, allowPage: Boolean, allowResources: Boolean): String = {
    if(destination.startsWith("abs:")) {
      val dest = destination.substring(4)
      logger.debug(s"Page $pageURI: Rewriting $tpe $destination to $dest")
      dest
    } else {
      val uri = pageURI.resolve(destination)
      uri.getScheme match {
        case "site" =>
          (if(allowPage) site.getPageFor(uri) else None) match {
            case Some(t) =>
              logger.debug(s"Page $pageURI: Resolved $tpe $destination to page ${t.uri}")
              val turi = t.uriWithSuffix(suffix)
              val turi2 = new URI(turi.getScheme, turi.getAuthority, turi.getPath, uri.getQuery, uri.getFragment)
              Util.relativeSiteURI(pageURI, turi2).toString
            case None =>
              if(!resourcePaths.contains(uri.getPath)) {
                val what = if(allowPage) "page or resource" else "resource"
                logger.error(s"Page $pageURI: No $what found for $tpe $destination (resolved to $uri)")
              }
              Util.relativeSiteURI(pageURI, uri).toString
          }
        case "theme" | "webjar" | "classpath" if allowResources =>
          try imageResources.get(uri.toString, null, false).toString
          catch { case ex: Exception =>
            logger.error(s"Page $pageURI: Error resolving $tpe with resource URI", ex)
            destination
          }
        case _ => destination
      }
    }
  }
}
