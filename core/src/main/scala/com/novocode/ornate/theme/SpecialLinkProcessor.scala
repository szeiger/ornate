package com.novocode.ornate.theme

import java.net.URI

import com.novocode.ornate.commonmark.PageProcessor
import com.novocode.ornate.{Logging, Util, Page, Site}
import org.commonmark.node.{Image, Link, AbstractVisitor}

/** Resolve links and image targets to the proper destination. */
class SpecialLinkProcessor(imageResources: Resources, site: Site, suffix: String, indexPage: Option[String], resourcePaths: Set[String]) extends PageProcessor with Logging {
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

  def resolve(pageURI: URI, destination: String, tpe: String, allowPage: Boolean, allowResources: Boolean, unchecked: Boolean = false): String = {
    if(destination.startsWith("abs:")) {
      val dest = destination.substring(4)
      logger.debug(s"Page $pageURI: Rewriting $tpe $destination to $dest")
      dest
    } else if(destination.startsWith("unchecked:")) {
      resolve(pageURI, destination.substring(10), tpe, allowPage, allowResources, unchecked = true)
    } else try {
      val uri = pageURI.resolve(destination)
      uri.getScheme match {
        case "site" =>
          val rel = (if(allowPage) site.getPageFor(uri) else None) match {
            case Some(t) =>
              logger.debug(s"Page $pageURI: Resolved $tpe $destination to page ${t.uri}")
              val turi = t.uriWithSuffix(suffix)
              val frag = uri.getFragment
              if(!unchecked && (frag ne null) && !t.headingIDs.contains(frag))
                logger.error(s"Page $pageURI: Fragment #$frag for $tpe $destination (resolved to $uri) not found")
              val turi2 = new URI(turi.getScheme, turi.getAuthority, turi.getPath, uri.getQuery, frag)
              Util.relativeSiteURI(pageURI, turi2)
            case None =>
              if(!unchecked && !resourcePaths.contains(uri.getPath)) {
                val what = if(allowPage) "page or resource" else "resource"
                logger.warn(s"Page $pageURI: No $what found for $tpe $destination (resolved to $uri)")
              }
              Util.relativeSiteURI(pageURI, uri)
          }
          Util.rewriteIndexPageLink(rel, indexPage).toString
        case "theme" | "webjar" | "classpath" if allowResources =>
          try imageResources.get(uri.toString, null, false).toString
          catch { case ex: Exception =>
            logger.error(s"Page $pageURI: Error resolving $tpe with resource URI", ex)
            destination
          }
        case _ => destination
      }
    } catch { case ex: Exception =>
      logger.error(s"Page $pageURI: Error processing link: $destination", ex)
      destination
    }
  }
}
