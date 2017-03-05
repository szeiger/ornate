package com.novocode.ornate

import java.net.URI
import java.util.Locale

import com.novocode.ornate.config.UserConfig
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue

object TocParser extends Logging {
  def parse(config: UserConfig, pages: Vector[Page]): Vector[TocEntry] = {
    val pagesByURI = pages.map(p => (p.uri.toString, p)).toMap

    def parseURI(link: String): Option[URI] = {
      if(link eq null) {
        logger.warn("Missing URL in TOC entry")
        None
      } else {
        try Some(Util.siteRootURI.resolve(link))
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
          (c.getStringOr("url"), c.getStringOr("title"))
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
        TocEntry(page, Option(title2))
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
