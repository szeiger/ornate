package com.novocode.mdoc

import java.util.Locale

import com.novocode.mdoc.config.UserConfig

import scala.collection.JavaConverters._

import java.net.URI

import com.typesafe.config.{ConfigObject, ConfigValue}
import org.slf4j.LoggerFactory

object TocParser {
  val logger = LoggerFactory.getLogger(getClass.getName.dropRight(1))

  def parse(config: UserConfig, pages: Vector[Page]): Vector[TocEntry] = {
    val pagesByURI = pages.map(p => (p.uri.toString, p)).toMap

    def parseURI(link: String): Option[URI] = {
      if(link eq null) {
        logger.warn("Missing URL in TOC entry")
        None
      } else {
        try Some(Util.docRootURI.resolve(link))
        catch { case ex: Exception =>
          logger.warn(s"Error parsing TOC URL: $link", ex)
          None
        }
      }
    }

    def parseTocEntry(v: ConfigValue): Option[TocEntry] = {
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
          else page.section.findFirstHeading.map(_.title).getOrElse {
            logger.warn(s"No TOC title defined for page $link")
            link
          }
        TocEntry(page, title2)
      }
    }

    val toc = config.toc match {
      case Some(v) =>
        logger.info("Parsing TOC")
        v.flatMap(parseTocEntry)
      case None =>
        logger.info("No TOC defined")
        Vector.empty
    }

    if(logger.isInfoEnabled) {
      toc.foreach { entry =>
        logger.info("[Page] "+entry.title)
        def logSection(s: Section, prefix: String): Unit = {
          s match {
            case s: HeadingSection =>
              logger.info(s"$prefix[${s.level}] ${s.title} #${s.id}")
            case s: PageSection =>
              logger.info(s"$prefix[${s.level}] ${s.title.getOrElse("")}")
            case _ =>
              logger.info(s"$prefix[${s.level}]")
          }
          s.children.foreach(ch => logSection(ch, "  "+prefix))
        }
        logSection(entry.page.section, "  ")
      }
    }
    toc
  }
}
