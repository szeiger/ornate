package com.novocode.ornate.highlight

import java.net.URI

import com.novocode.ornate.commonmark.{AttributeFencedCodeBlocksProcessor, PageProcessor}
import com.novocode.ornate.commonmark.NodeExtensionMethods.nodeToNodeExtensionMethods
import com.novocode.ornate.{Page, Util}
import com.novocode.ornate.config.{ConfiguredObject, Global}
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import org.commonmark.node._
import play.twirl.api.HtmlFormat

/** Base class for highlighters */
abstract class Highlighter(co: ConfiguredObject) extends PageProcessor {
  /** Convert source code or other plain text to HTML with highlighting applied. Use the
    * language name, if supplied, otherwise guess or format without highlighting. */
  def highlightTextAsHTML(text: String, lang: Option[String], node: Node, page: Page): HighlightResult

  override def apply(page: Page): Unit = {
    val ignore = co.getConfig(page.config).getStringListOr("ignore")
    def wrap(hl: Node with Highlit, text: String, lang: Option[String], n: Node): Unit = {
      hl.result = highlightTextAsHTML(text, lang, n, page)
      n.replaceWith(hl)
      hl.appendChild(n)
    }
    page.doc.accept(new AbstractVisitor {
      override def visit(_n: FencedCodeBlock): Unit = {
        val n = AttributeFencedCodeBlocksProcessor.lift(_n)
        n.getLanguage match {
          case Some(s) if ignore.contains(s) => // do nothing
          case lang => wrap(new HighlitBlock, n.getLiteral, lang, n)
        }
      }
      override def visit(n: Code): Unit = wrap(new HighlitInline, n.getLiteral, None, n)
      override def visit(n: IndentedCodeBlock): Unit = wrap(new HighlitBlock, n.getLiteral, None, n)
    })
  }
}

case class HighlightResult(html: String, language: Option[String],
                           css: Iterable[URI] = Seq.empty,
                           preClasses: Seq[String] = Seq.empty, preCodeClasses: Seq[String] = Seq.empty, codeClasses: Seq[String] = Seq.empty)

object HighlightResult {
  def simple(text: String, language: Option[String] = None): HighlightResult =
    apply(HtmlFormat.escape(Util.trimLines(text)).toString, language)
}

trait Highlit { this: Node =>
  var result: HighlightResult = null
}

class HighlitBlock extends CustomBlock with Highlit

class HighlitInline extends CustomNode with Highlit
