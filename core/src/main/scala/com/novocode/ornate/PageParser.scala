package com.novocode.ornate

import java.net.URI

import com.novocode.ornate.commonmark.{AttributedHeading, HeadingAccumulator, TextAccumulator}
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.config.{ReferenceConfig, Global}
import com.typesafe.config.Config

import org.commonmark.node.{Document, Node, Heading}
import org.commonmark.parser.Parser

import better.files._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Codec

object PageParser extends Logging {
  def parseSources(global: Global): Vector[Page] = {
    val sources = global.findSources
    logger.info(s"Parsing ${sources.length} source files")
    sources.flatMap { case (f, suffix, uri) =>
      logger.debug(s"Parsing $f as $uri")
      try Some(parseWithFrontMatter(Some(f.uri), global.userConfig, uri, suffix, f.contentAsString(Codec.UTF8)))
      catch { case ex: Exception =>
        logger.error(s"Error parsing $f -- skipping file", ex)
        None
      }
    }
  }

  def parseWithFrontMatter(sourceFileURI: Option[URI], globalConfig: ReferenceConfig, uri: URI, suffix: String, text: String): Page = {
    val lines = text.lines
    val (front, content) = if(lines.hasNext && lines.next.trim == "---") {
      var foundEnd = false
      val front = lines.takeWhile { s =>
        foundEnd = s.trim == "---"
        !foundEnd
      }.mkString("", "\n", "\n")
      val rest = lines.mkString("", "\n", "\n")
      if(!foundEnd) {
        logger.warn(s"Page $uri: Found top marker for front matter but no bottom marker, assuming it is all content")
        ("", text)
      } else (front, rest)
    } else ("", text)

    val pageConfig = if(front.nonEmpty) globalConfig.parsePageConfig(front) else globalConfig.raw

    parseContent(sourceFileURI, globalConfig, uri, suffix, content, pageConfig)
  }

  def parseContent(sourceFileURI: Option[URI], appConfig: ReferenceConfig, uri: URI, suffix: String, content: String, pageConfig: Config): Page = {
    val extensions = appConfig.getExtensions(pageConfig.getStringList("extensions").asScala)
    if(logger.isDebugEnabled) logger.debug("Page extensions: " + extensions)

    val parser = Parser.builder().extensions(extensions.parser.asJava).build()
    val doc = parser.parse(content)

    val sections = computeSections(uri, doc)
    val title =
      if(pageConfig.hasPath("title")) Some(pageConfig.getString("title"))
      else UntitledSection(0, sections).findFirstHeading.map(_.title)
    new Page(sourceFileURI, uri, suffix, doc, pageConfig, PageSection(title, sections), extensions, parser)
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
    if(logger.isDebugEnabled) {
      logger.debug("Sections:")
      withFullIDsAndTitles.foreach { case (h, id, title) =>
        logger.debug(s"- $id: $title")
      }
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
