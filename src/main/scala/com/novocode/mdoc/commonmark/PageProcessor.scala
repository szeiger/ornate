package com.novocode.mdoc.commonmark

import com.novocode.mdoc.{Global, Page}
import NodeExtensionMethods._

import org.commonmark.node.{AbstractVisitor, Image, Paragraph}
import org.slf4j.LoggerFactory

abstract class PageProcessor extends (Page => Unit)

class SpecialImageProcessor(global: Global) extends PageProcessor {
  val logger = LoggerFactory.getLogger(getClass)

  val SpecialObjectMatcher = new SpecialImageParagraphMatcher(Set("toctree"))

  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Paragraph): Unit = n match {
        case SpecialObjectMatcher(r) => r.protocol match {
          case "toctree" =>
            val t = new TocBlock(r.attributes.get("maxlevel").map(_.toInt).getOrElse(global.tocMaxLevel), r.title)
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
}
