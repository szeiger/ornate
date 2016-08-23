package com.novocode.ornate.commonmark

import java.net.URI
import java.util.Locale

import com.novocode.ornate._
import NodeExtensionMethods._
import com.novocode.ornate.config.UserConfig

import org.commonmark.node._

import scala.collection.mutable.ArrayBuffer

class SpecialImageProcessor(config: UserConfig) extends PageProcessor {
  val SpecialObjectMatcher = new SpecialImageParagraphMatcher(Set("toctree", "config"))

  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    override def visit(n: Paragraph): Unit = n match {
      case SpecialObjectMatcher(r) => r.protocol match {
        case "toctree" =>
          try {
            val t = SpecialImageProcessor.parseTocURI(r.image.getDestination, config)
            t.title = r.title
            n.replaceWith(t)
            r.image.children.foreach(t.appendChild)
          } catch { case ex: Exception => logger.error("Error expanding TOC tree "+r.dest, ex) }
          case _ =>
        }
      case n => super.visit(n)
    }
    override def visit(n: Image): Unit = {
      n match {
        case SpecialObjectMatcher.ImageMatcher(r) => r.protocol match {
          case "config" =>
            try {
              val key = new URI(r.image.getDestination).getSchemeSpecificPart
              val s = p.config.getString(key)
              r.image.replaceWith(new Text(s))
            } catch { case ex: Exception =>
              logger.error(s"""Page ${p.uri}: Error expanding config reference "${r.image.getDestination}"""", ex)
              n.unlink()
            }
          case _ =>
            logger.error(s"Page ${p.uri}: Illegal inline special object ${r.image.getDestination} -- only block-level allowed")
        }
        case n =>
      }
      super.visit(n)
    }
  })
}

object SpecialImageProcessor {
  def parseTocURI(link: String, config: UserConfig): TocBlock = {
    val uri = if(link == "toctree:") new URI("toctree:default") else new URI(link)
    val scheme = uri.getScheme
    assert(scheme == "toctree")
    val attributes = uri.getSchemeSpecificPart.split(',').filter(_.nonEmpty).flatMap { s =>
      val sep = s.indexOf('=')
      if(sep == -1) None else Some((s.substring(0, sep).toLowerCase(Locale.ENGLISH), s.substring(sep+1)))
    }.toMap
    val maxLevel = attributes.get("maxlevel").map(_.toInt).getOrElse(config.tocMaxLevel)
    new TocBlock(
      maxLevel,
      attributes.get("mergefirst").map(_.toBoolean).getOrElse(config.tocMergeFirst),
      attributes.get("local").map(_.toBoolean).getOrElse(false),
      attributes.get("focusmaxlevel").map(_.toInt).getOrElse(maxLevel)
    )
  }
}
