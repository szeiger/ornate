package com.novocode.mdoc

import java.util.Locale

import scala.collection.JavaConverters._

import java.net.URI

import com.typesafe.config.{ConfigObject, ConfigValue}
import org.slf4j.LoggerFactory

class Toc(val entries: Vector[Toc.Entry])

object Toc {
  val logger = LoggerFactory.getLogger(getClass.getName.dropRight(1))

  def apply(global: Global, pages: Vector[Page]): Toc = {
    val pagesByURI = pages.map(p => (p.uri.toString, p)).toMap

    def parseURI(link: String): Option[URI] = {
      if(link eq null) {
        logger.warn("Missing URL in TOC entry")
        None
      } else {
        try Some(global.docRootURI.resolve(link))
        catch { case ex: Exception =>
          logger.warn(s"Error parsing TOC URL: $link", ex)
          None
        }
      }
    }

    def parseTocEntry(v: ConfigValue): Option[Entry] = {
      logger.debug(s"Parsing TOC entry: $v")
      val (link, title) = v match {
        case v: ConfigObject =>
          val c = v.toConfig
          val link = if(c.hasPath("url")) c.getString("url") else null
          val title = if(c.hasPath("title")) c.getString("title") else null
          (link, title)
        case v =>
          (String.valueOf(v.unwrapped()), null)
      }
      for {
        uri <- parseURI(link)
        page <- pagesByURI.get(uri.toString).orElse {
          logger.warn(s"URL in TOC not found: $link")
          None
        }
      } yield {
        val title2 =
          if(title ne null) title
          else page.sections.headOption.map(_.title).getOrElse {
            logger.warn(s"No TOC title defined for page $link")
            link
          }
        Entry(page, title2)
      }
    }

    val toc =
      if(global.config.hasPath("global.toc")) {
        logger.debug("Parsing TOC")
        global.config.getList("global.toc").asScala.toVector.flatMap(parseTocEntry)
      } else {
        logger.debug("No TOC defined")
        Vector.empty
      }

    if(logger.isInfoEnabled) {
      toc.foreach { entry =>
        logger.info("[Page] "+entry.title)
        def logSection(s: Section, prefix: String): Unit = {
          logger.info(s"$prefix[${s.level}] ${s.title} #${s.id}")
          s.children.foreach(ch => logSection(ch, "  "+prefix))
        }
        entry.page.sections.foreach(s => logSection(s, "  "))
      }
    }
    new Toc(toc)
  }

  case class Entry(val page: Page, val title: String)
}
