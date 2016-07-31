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

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Codec

object PageParser {
  val logger = LoggerFactory.getLogger(getClass.getName.dropRight(1))

  def parse(global: Global, f: File): Page = {
    val uri = {
      val segments = global.sourceDir.relativize(f).iterator().asScala.toVector.map(s => URLEncoder.encode(s.toString, "UTF-8"))
      val path = (segments.init :+ segments.last.replaceAll("\\.[Mm][Dd]$", "")).mkString("/", "/", "")
      global.docRootURI.resolve(path)
    }
    logger.info(s"Parsing $f as $uri")
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
        case (a, Some(o)) =>
          val types = Seq(
            if(o.isInstanceOf[Extension]) Some("e") else None,
            if(o.isInstanceOf[ParserExtension]) Some("p") else None,
            if(o.isInstanceOf[HtmlRendererExtension]) Some("h") else None
          ).flatten
          if(types.isEmpty) a else types.mkString(s"$a[", ",", "]")
      }.mkString(", "))

    val parserExtensions = extensions.collect { case (_, Some(e: ParserExtension)) => e }.asJava
    val parser = Parser.builder().extensions(parserExtensions).build()
    val doc = parser.parse(content)

    val sections = computeSections(uri, doc)
    val title =
      if(config.hasPath("title")) Some(config.getString("title"))
      else UntitledSection(0, sections).findFirstHeading.map(_.title)
    new Page(uri, doc, config, PageSection(title, sections), extensions.collect { case (_, Some(o)) => o })
  }

  private def computeSections(uri: URI, doc: Node): Vector[Section] = {
    val headings = doc.compute(new HeadingAccumulator).map {
      case h: AttributedHeading => (h, h.id)
      case h =>
        val a = new AttributedHeading
        a.setLevel(h.getLevel)
        h.replaceWith(a)
        h.children.foreach(a.appendChild)
        (a, null)
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
        logger.warn(s"Renaming duplicate section ID #$id in $uri to #$id2")
        h.id = id2
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

    @tailrec
    def lift(level: Int, s: Section): Section =
      if(s.level <= level) s
      else lift(level, UntitledSection(s.level-1, Vector(s)))

    def mergeEmpty(ss: Vector[Section]): Vector[Section] = if(ss.isEmpty) ss else {
      val b = new ArrayBuffer[Section]
      ss.foreach {
        case s: UntitledSection if b.nonEmpty =>
          b.last match {
            case s2: UntitledSection =>
              b.remove(b.length-1)
              b += new UntitledSection(s2.level, mergeEmpty(s2.children ++ s.children))
            case _ => b += s
          }
        case s => b += s
      }
      b.toVector
    }

    def create(level: Int, headings: List[(Heading, String, String)]): List[Section] = headings match {
      case Nil => Nil
      case h :: hs =>
        val (lower, rest) = hs.span(_._1.getLevel > h._1.getLevel)
        val children = mergeEmpty(create(level+1, lower).toVector)
        lift(level, new HeadingSection(h._2, h._1.getLevel, h._3, children)(h._1)) :: create(level, rest)
    }

    mergeEmpty(create(1, withFullIDsAndTitles).toVector)
  }
}
