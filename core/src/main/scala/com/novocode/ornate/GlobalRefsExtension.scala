package com.novocode.ornate

import scala.collection.JavaConverters._

import com.novocode.ornate.config.ConfiguredObject
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

/** Prepend global reference definitions to all pages */
class GlobalRefsExtension(co: ConfiguredObject) extends Extension with Logging {
  val preludeConfig = co.memoizeParsed { c =>
    val defs = c.getObject("refs").entrySet().iterator().asScala.map { e =>
      val (link, title) = e.getValue match {
        case v: ConfigObject =>
          val c = v.toConfig
          (c.getStringOr("url"), c.getStringOr("title"))
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

  override def preProcessors(pageConfig: Config): Seq[PreProcessor] = Seq(new PreProcessor {
    val prelude = preludeConfig(pageConfig)
    def apply(s: String): String = if(prelude.isEmpty) s else prelude + s
  })
}
