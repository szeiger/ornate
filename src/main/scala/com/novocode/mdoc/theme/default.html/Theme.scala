package com.novocode.mdoc.theme.default.html

import java.net.URI

import com.novocode.mdoc.commonmark.{TabItem, TabView, AttributedBlockQuote, AttributedHeading}
import com.novocode.mdoc.commonmark.NodeExtensionMethods._
import com.novocode.mdoc.config.Global
import com.novocode.mdoc.highlight.HighlightResult
import com.novocode.mdoc.{PageParser, Page, Util}
import com.novocode.mdoc.theme.HtmlTheme
import org.commonmark.html.renderer.NodeRendererContext

import scala.collection.JavaConverters._

class Theme(global: Global) extends HtmlTheme(global) {

  override def synthesizePages(uris: Vector[(String, URI)]): Vector[Page] = {
    uris.flatMap {
      case ("toc", u) =>
        logger.debug(s"Creating TOC page $u")
        Some(PageParser.parseContent(global.referenceConfig, u, ".md",
          "# Table of Contents\n\n![Table of Contents](toctree:)",
          global.userConfig.parsePageConfig("title: Table of Contents"), true))
      case (name, u) =>
        logger.warn(s"""Unknown extra page name "$name" in theme configuration -- ignoring""")
        None
    }
  }

  override def renderAttributedHeading(n: AttributedHeading, c: NodeRendererContext): Unit = if(n.id ne null) {
    val wr = c.getHtmlWriter
    val htag = s"h${n.getLevel}"
    wr.line
    val attrs = Map[String, String]("id" -> n.id, "class" -> "a_section", "data-magellan-target" -> n.id)
    wr.tag(htag, c.extendAttributes(n, attrs.asJava))
    n.children.toVector.foreach(c.render)
    wr.raw(s"""<a class="a_hlink" href="#${n.id}"></a>""")
    wr.tag('/' + htag)
    wr.line
  } else super.renderAttributedHeading(n, c)

  override def renderAttributedBlockQuote(n: AttributedBlockQuote, c: NodeRendererContext): Unit = {
    val callout =
      if(n.simpleAttrs.contains(".warning")) Some("warning")
      else if(n.simpleAttrs.contains(".note")) Some("primary")
      else None
    callout match {
      case Some(s) =>
        val wr = c.getHtmlWriter
        wr.line
        wr.raw(s"""<div class="callout $s">""")
        wr.line
        n.children.toVector.foreach(c.render)
        wr.line
        wr.raw("</div>")
        wr.line
      case None => super.renderAttributedBlockQuote(n, c)
    }
  }

  override def renderCode(hlr: HighlightResult, c: NodeRendererContext, block: Boolean): Unit = if(block) {
    val wr = c.getHtmlWriter
    wr.line
    wr.tag("div", Map("class" -> "row").asJava)
    super.renderCode(hlr.copy(preClasses = hlr.preClasses ++ Seq("small-expand", "columns", "a_xscroll")), c, block)
    wr.tag("/div")
    wr.line
  } else super.renderCode(hlr, c, block)

  override def renderTabView(pc: PageContext)(n: TabView, c: NodeRendererContext): Unit = {
    val id = pc.newID()
    val items = n.children.collect { case i: TabItem => (i, pc.newID()) }.zipWithIndex.toVector
    val wr = c.getHtmlWriter
    wr.line
    wr.raw(s"""<ul class="tabs" data-tabs id="$id">""")
    items.foreach { case ((item, itemID), idx) =>
      val active = if(idx == 0) " is-active" else ""
      val aria = if(idx == 0) """ aria-selected="true"""" else ""
      wr.raw(s"""<li class="tabs-title$active"><a href="#$itemID"$aria>""")
      wr.text(item.title)
      wr.raw("</a></li>")
    }
    wr.raw("</ul>")
    wr.line
    wr.raw(s"""<div class="tabs-content" data-tabs-content="$id">""")
    items.foreach { case ((item, itemID), idx) =>
      val active = if(idx == 0) " is-active" else ""
      wr.raw(s"""<div class="tabs-panel$active" id="$itemID">""")
      item.children.toVector.foreach(c.render)
      wr.raw("</div>")
    }
    wr.raw("</div>")
    wr.line
  }
}
