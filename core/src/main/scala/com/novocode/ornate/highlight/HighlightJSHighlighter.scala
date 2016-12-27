package com.novocode.ornate.highlight

import java.net.URI

import com.novocode.ornate.commonmark.PageProcessor
import com.novocode.ornate.js.{JSMap, NashornSupport, WebJarSupport}
import com.novocode.ornate.{Extension, Logging, Page, Site}
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.novocode.ornate.config.ConfiguredObject
import org.commonmark.node.{FencedCodeBlock, IndentedCodeBlock, Node}
import play.twirl.api.HtmlFormat

import scala.collection.mutable

/** A Highlighter based on highlight.js */
class HighlightJSHighlighter(co: ConfiguredObject) extends Highlighter(co) with NashornSupport with Logging {
  logger.debug("Loading highlight.js...")
  private[this] val hljs = mainModule.require("highlight.js/lib/highlight.js")
  private[this] val supportedLanguages = WebJarSupport.listAssets("highlight.js", "lib/languages/").map(_.replaceAll("\\.js$", ""))
  logger.debug("Supported languages: "+supportedLanguages.mkString(", "))

  private[this] val tried, loaded = new mutable.HashSet[String]

  def requireLanguages(langs: Iterable[String]): Vector[String] = synchronized {
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

  def highlightTextAsHTML(text: String, lang: Option[String], target: Node, page: Page): HighlightResult = synchronized { try {
    val localConf = co.getConfig(page.config)
    localConf.getStringListOpt("preload").foreach(requireLanguages)
    val langs: Vector[String] = lang match {
      case Some("text") => Vector.empty
      case Some(l) => Vector(l)
      case None =>
        localConf.getStringOrStringList(target match {
          case _: FencedCodeBlock   => "fenced"
          case _: IndentedCodeBlock => "indented"
          case _                    => "inline"
        })
    }
    val css = {
      val base = new URI("webjar:/highlight.js/styles/")
      localConf.getStringListOr("styleResources").map(base.resolve _)
    }
    def textHighlight(text: String) =
      HighlightResult(HtmlFormat.escape(text).toString, None, css, preClasses = Seq("hljs"), codeClasses = Seq("hljs"))
    if(langs.isEmpty) textHighlight(text)
    else {
      val legalLangs = requireLanguages(langs)
      if(legalLangs.isEmpty) textHighlight(text)
      else {
        logger.debug("Highlighting as language "+legalLangs.mkString("/"))
        val o = legalLangs match {
          case Vector(l) => call[JSMap](hljs, "highlight", l, text)
          case v => call[JSMap](hljs, "highlightAuto", text, engine.invokeFunction("Array", v: _*))
        }
        HighlightResult(o[String]("value"), lang.orElse(Option(o[String]("language"))), css,
          preClasses = Seq("hljs"), codeClasses = Seq("hljs")
        )
      }
    }
  } catch { case ex: Exception =>
    logger.error("Error running highlight.js", ex)
    HighlightResult.simple(text)
  }}
}

class HighlightJSExtension(co: ConfiguredObject) extends Extension {
  override def pageProcessors(site: Site): Seq[PageProcessor] = Seq(new HighlightJSHighlighter(co))
}
