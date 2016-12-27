package com.novocode.ornate.theme

import java.net.URI

import com.novocode.ornate.URIExtensionMethods._
import com.novocode.ornate._
import org.commonmark.node.{AbstractVisitor, Image, Link, Text}

/** Resolve links and image targets to the proper destination. */
class SpecialLinkProcessor(imageResources: PageResources, lookup: SpecialLinkProcessor.Lookup, suffix: String, indexPage: Option[String], resourcePaths: Set[String]) extends PageProcessor with Logging {
  def runAt: Phase = Phase.Special

  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Link): Unit = {
        val (dest, titleO) = resolve(p.uri, n.getDestination, "link", true)
        if(n.getFirstChild eq null) titleO match {
          case Some(t) => n.appendChild(new Text(t))
          case None => logger.warn(s"Page ${p.uri}: Link to ${n.getDestination} is empty")
        }
        n.setDestination(dest)
      }
      override def visit(n: Image): Unit =
        n.setDestination(resolveLink(p.uri, n.getDestination, "image", false))
    })
  }

  def resolveLink(pageURI: URI, destination: String, tpe: String, isPage: Boolean, unchecked: Boolean = false): String =
    resolve(pageURI, destination, tpe, isPage)._1

  def resolve(pageURI: URI, destination: String, tpe: String, isPage: Boolean, unchecked: Boolean = false): (String, Option[String]) = {
    if(destination.startsWith("abs:")) {
      val dest = destination.substring(4)
      logger.debug(s"Page $pageURI: Rewriting $tpe $destination to $dest")
      (dest, None)
    } else if(destination.startsWith("unchecked:")) {
      resolve(pageURI, destination.substring(10), tpe, isPage, unchecked = true)
    } else try {
      val uri = pageURI.resolve(destination)
      uri.getScheme match {
        case "site" =>
          val (rel, titleO) = (if(isPage) lookup.getPageFor(uri) else None) match {
            case Some(t) =>
              logger.debug(s"Page $pageURI: Resolved $tpe $destination to page ${t.uri}")
              val turi = t.uriWithSuffix(suffix)
              val frag = uri.getFragment
              val title: Option[String] = if(frag eq null) t.section.title else t.headingTitles.get(frag)
              if(!unchecked && (frag ne null) && title.isEmpty)
                logger.error(s"Page $pageURI: Fragment #$frag for $tpe $destination not found")
              (Util.relativeSiteURI(pageURI, turi.copy(fragment = frag)), title)
            case None =>
              if(!unchecked && !resourcePaths.contains(uri.getPath)) {
                val what = if(isPage) "page or resource" else "resource"
                logger.warn(s"Page $pageURI: No $what found for $tpe $destination")
              }
              (Util.relativeSiteURI(pageURI, uri), None)
          }
          (Util.rewriteIndexPageLink(rel, indexPage).toString, titleO)
        case "theme" | "webjar" | "classpath" if !isPage =>
          try { (imageResources.get(uri.toString, "images/").toString, None) }
          catch { case ex: Exception =>
            logger.error(s"Page $pageURI: Error resolving $tpe with resource URI", ex)
            (destination, None)
          }
        case _ => (destination, None)
      }
    } catch { case ex: Exception =>
      logger.error(s"Page $pageURI: Error processing link: $destination", ex)
      (destination, None)
    }
  }
}

object SpecialLinkProcessor {
  class Lookup(site: Site, targetSuffix: Option[String]) {
    private[this] val pageMap: Map[String, Page] = {
      val m = site.pages.map(p => (p.uri.getPath, p)).toMap
      targetSuffix match {
        case Some(s) => m ++ site.pages.map(p => (p.uriWithSuffix(s).getPath, p)).toMap
        case None => m
      }
    }

    def getPageFor(uri: URI): Option[Page] =
      if(uri.getScheme == Util.siteRootURI.getScheme) pageMap.get(uri.getPath)
      else None
  }
}
