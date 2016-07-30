package com.novocode.mdoc

import java.net.{URLEncoder, URI}

import com.novocode.mdoc.commonmark.{AttributedHeading, HeadingAccumulator, TextAccumulator}
import com.novocode.mdoc.commonmark.NodeExtensionMethods._

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.node.{Node, Heading}
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.html.HtmlRenderer
import org.commonmark.parser.Parser

import better.files._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Codec

object PageParser {
  val logger = LoggerFactory.getLogger(getClass.getName.dropRight(1))

  def parse(global: Global, f: File): Page = {
    val uri = {
      val segments = global.sourceDir.relativize(f).iterator().asScala.toVector.map(s => URLEncoder.encode(s.toString, "UTF-8"))
      val path = (segments.init :+ segments.last.replaceAll("\\.[Mm][Dd]$", "")).mkString("/", "/", "")
      global.docRootURI.resolve(path)
    }
    logger.debug(s"Parsing source file $f as $uri")
    val text = f.contentAsString(Codec.UTF8)
    val lines = text.lines
    val (front, content) = if(lines.hasNext && lines.next.trim == "---") {
      var foundEnd = false
      val front = lines.takeWhile { s =>
        foundEnd = s.trim == "---"
        !foundEnd
      }.mkString("", "\n", "\n")
      val rest = lines.mkString("", "\n", "\n")
      if(!foundEnd) {
        logger.warn(s"Top marker for front matter found in $f but no bottom marker, assuming it is all content")
        ("", text)
      } else (front, rest)
    } else ("", text)

    val config = if(front.nonEmpty) global.parsePageConfig(front) else global.config

    val extensions =
      global.normalizeExtensions(config.getStringList("extensions").asScala)
        .map(a => (a, global.getCachedExtensionObject(a)))

    if(logger.isDebugEnabled)
      logger.debug("Page extensions: " + extensions.map {
        case (a, None) => s"($a)"
        case (a, Some(o: ParserExtension with HtmlRendererExtension)) => s"$a[p,h]"
        case (a, Some(o: HtmlRendererExtension)) => s"$a[h]"
        case (a, Some(o: ParserExtension)) => s"$a[p]"
        case (a, Some(_)) => a
      }.mkString(", "))

    val parserExtensions = extensions.collect { case (_, Some(e: ParserExtension)) => e }.asJava
    val parser = Parser.builder().extensions(parserExtensions).build()
    val doc = parser.parse(content)

    new Page(uri, doc, config, computePageSections(uri, doc), extensions.collect { case (_, Some(o)) => o })
  }

  private def computePageSections(uri: URI, doc: Node): Vector[Section] = {
    def create(headings: List[(Heading, String, String)]): List[Section] = headings match {
      case Nil => Nil
      case h :: hs =>
        val (lower, rest) = hs.span(_._1.getLevel > h._1.getLevel)
        new Section(h._2, h._3, h._1, create(lower).toVector) :: create(rest)
    }
    val headings = doc.compute(new HeadingAccumulator).map {
      case h: AttributedHeading => (h, h.id)
      case h => (h, null)
    }
    val used = new mutable.HashSet[String]()
    def newID(id: String): String = {
      val id2 =
        if(used.contains(id)) Iterator.from(1).map(i => s"$id-$i").dropWhile(s => used.contains(s)).next
        else id
      used += id2
      id2
    }
    val withUniqueIDs = headings.map {
      case (h, null) => (h, null)
      case (h, id) if used.contains(id) =>
        val id2 = newID(id)
        logger.warn(s"Renaming duplicate section ID #$id in document $uri to #$id2")
        (h, id2)
      case (h, id) =>
        used += id
        (h, id)
    }
    val withFullIDsAndTitles = withUniqueIDs.map { case (h, id) =>
      val title = h.compute(new TextAccumulator)
      val id2 = if(id eq null) newID(Util.createIdentifier(title)) else id
      (h, id2, title)
    }
    create(withFullIDsAndTitles).toVector
  }
}
