package com.novocode.mdoc.highlight

import com.novocode.mdoc.Page
import com.novocode.mdoc.config.{ConfiguredObject, Global}
import play.twirl.api.{HtmlFormat, Html}

trait Highlighter {
  /** Convert source code or other plain text to HTML with highlighting applied. Use the
    * language name, if supplied, otherwise guess or format without highlighting. */
  def highlightTextAsHTML(text: String, lang: Option[String], target: HighlightTarget, page: Page): HighlightResult
}

case class HighlightResult(html: Html, language: Option[String])

class NoHighlighter(global: Global, conf: ConfiguredObject) extends Highlighter {
  def highlightTextAsHTML(text: String, lang: Option[String], target: HighlightTarget, page: Page): HighlightResult =
    HighlightResult(HtmlFormat.escape(text), None)
}

sealed trait HighlightTarget
object HighlightTarget {
  case object FencedCodeBlock extends HighlightTarget
  case object IndentedCodeBlock extends HighlightTarget
  case object InlineCode extends HighlightTarget
}
