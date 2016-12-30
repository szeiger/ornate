package com.novocode.ornate

import java.net.URI

import URIExtensionMethods._
import com.novocode.ornate.commonmark.CustomParser
import com.typesafe.config.Config
import org.commonmark.node._

class Site(val pages: Vector[Page], val toc: Vector[TocEntry]) {
  def findTocEntry(p: Page): Option[TocEntry] = toc.find(te => te.page eq p)
  def findTocLocation(p: Page): Option[TocLocation] = {
    val i = toc.indexWhere(_.page eq p)
    if(i < 0) None else {
      val prev = if(i > 0) Some(toc(i-1)) else None
      val next = if(i < toc.length-1) Some(toc(i+1)) else None
      Some(TocLocation(toc(i), prev, next))
    }
  }
}

class Page(val sourceFileURI: Option[URI], val syntheticName: Option[String],
           val uri: URI, val suffix: String, val doc: Node, val config: Config,
           val section: PageSection, val extensions: Extensions, parser: CustomParser) {
  override def toString: String = s"Page($uri)"

  def uriWithSuffix(ext: String): URI = uri.replaceSuffix(suffix, ext)

  var processors: Seq[PageProcessor] = null // Initialized by Theme after the Site object has been built

  def applyProcessors(): Unit = processors.foreach(_.apply(this))

  def parseAndProcessSnippet(content: String): Page = {
    val doc = parser.parse(content)
    val snippetPage = new Page(None, None, uri, suffix, doc, config, section, extensions, parser)
    processors.foreach(_.apply(snippetPage))
    snippetPage
  }

  lazy val headingTitles: Map[String, String] = section.allHeadings.map(s => (s.id, s.title)).toMap
}

sealed abstract class Section {
  def children: Vector[Section]
  def level: Int
  final def findFirstHeading: Option[HeadingSection] = {
    val it = allHeadings
    if(it.hasNext) Some(it.next) else None
  }
  def allHeadings: Iterator[HeadingSection] =
    children.iterator.flatMap(_.allHeadings)
  def getTitle: Option[String]
  def getID: Option[String]
  def recursiveTitleCount: Int =
    children.map(_.recursiveTitleCount).sum + getTitle.map(_ => 1).getOrElse(0)
}

final case class HeadingSection(id: String, level: Int, title: String, children: Vector[Section])(val heading: Heading) extends Section {
  override def allHeadings: Iterator[HeadingSection] =
    Iterator(this) ++ super.allHeadings
  def getTitle = Some(title)
  def getID = Some(id)
}

final case class UntitledSection(level: Int, children: Vector[Section]) extends Section {
  def getTitle = None
  def getID = None
}

final case class PageSection(title: Option[String], children: Vector[Section]) extends Section {
  def level = 0
  def getTitle = title
  def getID = None
}

case class TocEntry(page: Page, title: Option[String])

case class TocLocation(current: TocEntry, previous: Option[TocEntry], next: Option[TocEntry])
