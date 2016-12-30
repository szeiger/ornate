package com.novocode.ornate.commonmark

import java.net.URI
import java.util.Locale

import com.novocode.ornate._
import NodeExtensionMethods._
import com.novocode.ornate.config.UserConfig

import org.commonmark.node._

import scala.collection.mutable.ArrayBuffer

/** Replace "config" and "toctree" images and image paragraphs, and convert other matched images from
  * `Image` to `SpecialImage` nodes. */
class SpecialImageProcessor(config: UserConfig, extraInline: Set[String], extraBlock: Set[String]) extends PageProcessor with Logging {
  def runAt: Phase = Phase.Attribute

  val SpecialObjectMatcher = new SpecialImageParagraphMatcher(Set("toctree", "config") ++ extraBlock ++ extraInline)

  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    override def visit(n: Paragraph): Unit = n match {
      case SpecialObjectMatcher(r) =>
        r.protocol match {
          case "toctree" =>
            try {
              val t = SpecialImageProcessor.parseTocURI(r.image.getDestination, config)
              t.title = r.title
              n.replaceWith(t)
              r.image.children.foreach(t.appendChild)
            } catch { case ex: Exception => logger.error("Error expanding TOC tree "+r.dest, ex) }
          case s if extraBlock.contains(s) =>
            val si = new SpecialImageBlock
            si.destination = r.image.getDestination
            si.title = r.image.getTitle
            r.image.children.foreach(si.appendChild)
            n.replaceWith(si)
          case _ => super.visit(n)
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
          case s if extraInline.contains(s) =>
            val si = new SpecialImageInline
            si.destination = r.image.getDestination
            si.title = r.image.getTitle
            r.image.children.foreach(si.appendChild)
            r.image.replaceWith(si)
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

trait SpecialImage {
  var title: String = null

  private[this] var _destination: String = null
  def destination: String = _destination
  def destination_= (s: String): Unit = {
    _destination = s
    _destinationURI = null
  }

  private[this] var _destinationURI: URI = null
  def destinationURI: URI = {
    if((_destinationURI eq null) && (_destination ne null)) _destinationURI = new URI(_destination)
    _destinationURI
  }
}

class SpecialImageInline extends CustomNode with SpecialImage

class SpecialImageBlock extends CustomBlock with SpecialImage
