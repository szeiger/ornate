package com.novocode.mdoc.highlight

import java.net.URI

import com.novocode.mdoc.js.{JSMap, NashornSupport}
import com.novocode.mdoc.{Logging, Page}
import com.novocode.mdoc.config.{ConfiguredObject, Global}
import play.twirl.api.HtmlFormat

import scala.collection.mutable
import scala.collection.JavaConverters._

/** A Highlighter based on highlight.js */
class HighlightJSHighlighter(global: Global, conf: ConfiguredObject) extends Highlighter with NashornSupport with Logging {
  val noHighlight = new NoHighlighter(global, null)

  logger.debug("Loading highlight.js...")
  val hljs = mainModule.require("highlight.js/lib/highlight.js")
  val supportedLanguages = listAssets("highlight.js", "lib/languages/").map(_.replaceAll("\\.js$", ""))
  logger.debug("Supported languages: "+supportedLanguages.mkString(", "))

  val tried, loaded = new mutable.HashSet[String]

  def requireLanguages(langs: Iterable[String]): Vector[String] = {
    val toLoad = langs.filterNot(tried.contains)
    if(toLoad.nonEmpty) {
      tried ++= toLoad // even if loading fails, so we don't retry every time
      val legal = toLoad.filter(supportedLanguages.contains)
      logger.debug(s"Loading language support for ${legal.mkString(", ")}...")
      for(l <- legal) call[Unit](hljs, "registerLanguage", l, mainModule.require(s"highlight.js/lib/languages/$l.js"))
      val (found, notFound) = toLoad.partition(l => call[String](hljs, "getLanguage", l) ne null)
      loaded ++= found
      if(notFound.nonEmpty) logger.warn("Unsupported languages: "+notFound.mkString(", "))
      logger.debug(s"All registered languages: "+call[Vector[String]](hljs, "listLanguages").mkString(", "))
    }
    langs.filter(loaded.contains).toVector
  }

  def highlightTextAsHTML(text: String, lang: Option[String], target: HighlightTarget, page: Page): HighlightResult = try {
    val localConf = conf.getConfig(page.config)
    if(localConf.hasPath("preload"))
      requireLanguages(localConf.getStringList("preload").asScala)
    val langs: Vector[String] = lang match {
      case Some("text") => Vector.empty
      case Some(l) => Vector(l)
      case None =>
        val defaultPath = target match {
          case HighlightTarget.FencedCodeBlock   => "fenced"
          case HighlightTarget.IndentedCodeBlock => "indented"
          case HighlightTarget.InlineCode        => "inline"
        }
        if(!localConf.hasPath(defaultPath)) Vector.empty
        else localConf.getAnyRef(defaultPath) match {
          case s: String => Vector(s)
          case i: java.util.Collection[_] => i.asScala.toVector.asInstanceOf[Vector[String]]
        }
    }
    val css =
      if(localConf.hasPath("styleResources")) {
        val base = new URI("webjar:/highlight.js/styles/")
        localConf.getStringList("styleResources").asScala.map(base.resolve _)
      } else Nil
    if(langs.isEmpty) noHighlight.highlightTextAsHTML(text, lang, target, page)
    else {
      val legalLangs = requireLanguages(langs)
      if(legalLangs.isEmpty) noHighlight.highlightTextAsHTML(text, lang, target, page)
      else {
        logger.debug("Highlighting as language "+legalLangs.mkString("/"))
        val o = legalLangs match {
          case Vector(l) => call[JSMap](hljs, "highlight", l, text)
          case v => call[JSMap](hljs, "highlightAuto", text, engine.invokeFunction("Array", v: _*))
        }
        HighlightResult(HtmlFormat.raw(o[String]("value")), Option(o[String]("language")), css)
      }
    }
  } catch { case ex: Exception =>
    logger.error("Error running highlight.js", ex)
    noHighlight.highlightTextAsHTML(text, lang, target, page)
  }
}
