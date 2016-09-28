package com.novocode.ornate

import com.novocode.ornate.config.ConfiguredObject
import com.typesafe.config.{ConfigObject, Config}

import scala.collection.JavaConverters._

/** Prepend global reference definitions to all pages */
class GlobalRefsExtension(co: ConfiguredObject) extends Extension with Logging {
  lazy val sitePrelude = createPrelude(co.config)

  override def preProcessors(pageConfig: Config): Seq[PreProcessor] = Seq(new GlobalRefsPreProcessor(pageConfig))

  def createPrelude(c: Config): String = {
    val defs = c.getObject("refs").entrySet().iterator().asScala.map { e =>
      val (link, title) = e.getValue match {
        case v: ConfigObject =>
          val c = v.toConfig
          val link = if(c.hasPath("url")) c.getString("url") else null
          val title = if(c.hasPath("title")) c.getString("title") else null
          (link, title)
        case v =>
          (String.valueOf(v.unwrapped()), null)
      }
      (e.getKey, link, title)
    }.filter { case (key, url, _) =>
      if(url != null && url.nonEmpty) true
      else {
        logger.warn("Global ref \""+key+"\" does not have a non-empty link target")
        false
      }
    }.toSeq
    if(logger.isDebugEnabled) {
      defs.foreach { case (key, url, title) =>
        logger.debug(s"globalRef: $key -> $url, $title")
      }
    }
    defs.map { case (key, url, title) =>
      val escKey = key.replace("]", "\\]")
      val escTitle =
        if(title == null || title.isEmpty) ""
        else " \"" + title.replace("\"", "\\\"") + "\""
      s"[$escKey]: $url$escTitle\n"
    }.mkString
  }

  class GlobalRefsPreProcessor(pageConfig: Config) extends PreProcessor {
    // Refs are usually defined in the site config. Reuse a cached site prelude unless the page config differs:
    val prelude = {
      val c = co.getConfig(pageConfig)
      if(c eq co.config) sitePrelude else createPrelude(c)
    }

    def apply(s: String): String = if(prelude.isEmpty) s else prelude + s
  }
}
