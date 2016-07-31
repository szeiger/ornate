package com.novocode.mdoc.commonmark

import java.net.URI

import com.novocode.mdoc.{Site, Global, Page}
import NodeExtensionMethods._

import org.commonmark.node.{Link, AbstractVisitor, Image, Paragraph}
import org.slf4j.LoggerFactory

abstract class PageProcessor extends (Page => Unit)

class SpecialImageProcessor(global: Global) extends PageProcessor {
  val logger = LoggerFactory.getLogger(getClass)

  val SpecialObjectMatcher = new SpecialImageParagraphMatcher(Set("toctree"))

  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    override def visit(n: Paragraph): Unit = n match {
      case SpecialObjectMatcher(r) => r.protocol match {
        case "toctree" =>
          val t = new TocBlock(
            r.attributes.get("maxlevel").map(_.toInt).getOrElse(global.tocMaxLevel),
            r.title,
            r.attributes.get("mergefirst").map(_.toBoolean).getOrElse(global.tocMergeFirst))
          n.replaceWith(t)
          r.image.children.foreach(t.appendChild)
        case _ =>
      }
      case n => super.visit(n)
    }
    override def visit(n: Image): Unit = {
      n match {
        case SpecialObjectMatcher.ImageMatcher(r) =>
          logger.error(s"Page ${p.uri}: Illegal inline special object ${r.image.getDestination} -- only block-level allowed")
        case n =>
      }
      super.visit(n)
    }
  })
}

class SpecialLinkProcessor(global: Global, site: Site) extends PageProcessor {
  val logger = LoggerFactory.getLogger(getClass)

  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Link): Unit = {
        val uri = p.uri.resolve(n.getDestination)
        if(uri.getScheme == "doc") {
          logger.debug(s"Page ${p.uri}: Resolved link ${n.getDestination} to $uri")
        }
        super.visit(n)
      }
      override def visit(n: Image): Unit = {
        super.visit(n)
      }
    })
  }
}
