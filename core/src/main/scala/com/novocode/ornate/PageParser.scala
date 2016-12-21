package com.novocode.ornate

import java.net.URI

import com.novocode.ornate.commonmark.{AttributedHeading, CustomParser, CustomParserBuilder, NodeUtil}
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.novocode.ornate.config.{Global, ReferenceConfig}
import com.typesafe.config.{Config, ConfigException}
import org.commonmark.node.{Document, Heading, Node}
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
    logTime("Parsing took") {
      global.parMap(sources) { case (f, suffix, uri) =>
        logger.debug(s"Parsing $f as $uri")
        try Some(parseWithFrontMatter(Some(f.uri), global.userConfig, uri, suffix, f.contentAsString(Codec.UTF8)))
        catch { case ex: Exception =>
          logger.error(s"Error parsing $f -- skipping file", ex)
          None
        }
      }.flatten
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

    val pageConfig = if(front.nonEmpty) {
      try globalConfig.parsePageConfig(front)
      catch {
        case ex: ConfigException =>
          logger.error(s"Page $uri: Error parsing front matter: "+ex.getMessage)
          globalConfig.raw
        case ex: Exception =>
          logger.error(s"Page $uri: Error parsing front matter", ex)
          globalConfig.raw
      }
    } else globalConfig.raw

    parseContent(sourceFileURI, globalConfig, uri, suffix, Some(content), pageConfig)
  }

  def parseContent(sourceFileURI: Option[URI], appConfig: ReferenceConfig, uri: URI, suffix: String, content: Option[String], pageConfig: Config): Page = {
    val extensions = appConfig.getExtensions(pageConfig.getStringList("extensions").asScala)
    if(logger.isDebugEnabled) logger.debug(s"Page $uri: Extensions: " + extensions)

    val parser = new CustomParser((new CustomParserBuilder).extensions(extensions.parser(pageConfig).asJava).asInstanceOf[CustomParserBuilder])
    val pre = extensions.ornate.flatMap(_.preProcessors(pageConfig))
    val (doc, sections) = content match {
      case Some(s) =>
        val preprocessedContent = pre.foldLeft(s) { case (s, f) => f(s) }
        val doc = parser.parse(preprocessedContent)
        val sections = computeSections(uri, doc)
        (doc, sections)
      case None => (new Document, Vector.empty)
    }
    val title = pageConfig.getStringOpt("title").orElse(UntitledSection(0, sections).findFirstHeading.map(_.title))
    new Page(sourceFileURI, uri, suffix, doc, pageConfig, PageSection(title, sections), extensions, parser)
  }

  private def computeSections(uri: URI, doc: Node): Vector[Section] = {
    val headings = NodeUtil.findHeadings(doc).map {
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
        logger.warn(s"Page $uri: Renaming duplicate section ID #$id to #$id2")
        h.id = id2
        (h, id2)
      case (h, id) =>
        used += id
        (h, id)
    }
    val withFullIDsAndTitles = withUniqueIDs.map { case (h, id) =>
      val title = NodeUtil.extractText(h).trim
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

    mergeEmpty(create(1, withFullIDsAndTitles.toList).toVector)
  }
}
