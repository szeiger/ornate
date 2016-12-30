package com.novocode.ornate.theme.default

import java.net.{URI, URLDecoder}
import java.util.Collections

import com.novocode.ornate.commonmark._
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.config.Global
import com.novocode.ornate.highlight.HighlightResult
import com.novocode.ornate._
import com.novocode.ornate.js.WebJarSupport
import com.novocode.ornate.theme.{HtmlPageContext, HtmlPageModel, HtmlTheme, PageResources}
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.renderer.html.{HtmlNodeRendererContext, HtmlNodeRendererFactory, HtmlRenderer}
import org.commonmark.node.{Block, Document, Node}

import scala.collection.JavaConverters._

class DefaultTheme(global: Global) extends HtmlTheme(global) {
  override def synthesizePages: Vector[Page] = {
    syntheticPageURIs.flatMap {
      case ("toc", u) =>
        logger.debug(s"Creating TOC page $u")
        Some(PageParser.parseContent(None, Some("toc"), global.userConfig, u, ".md",
          Some(global.userConfig.theme.config.getString("strings.tocPage")),
          global.userConfig.raw))
      case ("search", u) =>
        logger.debug(s"Creating search page $u")
        Some(PageParser.parseContent(None, Some("search"), global.userConfig, u, ".md",
          Some(global.userConfig.theme.config.getString("strings.searchPage")),
          global.userConfig.theme.config.getConfig("global.searchPageConfig").withFallback(global.userConfig.raw)))
      case (name, u) =>
        logger.warn(s"""Unknown extra page name "$name" in theme configuration -- ignoring""")
        None
    }
  }

  override def specialImageSchemesInline: Set[String] = Set("foundation-icon")

  override def renderSpecialImageInline(pc: HtmlPageContext)(n: SpecialImageInline, c: HtmlNodeRendererContext): Unit = {
    if(n.destinationURI.getScheme == "foundation-icon") {
      val name = n.destinationURI.getSchemeSpecificPart
      FoundationIcons.icons.get(name) match {
        case Some(glyph) =>
          val wr = c.getWriter
          wr.raw(s"""<span class="a_foundation_icon">$glyph</span>""")
          pc.res.get("foundation-icons.custom.css", "css/", createLink = true)
          pc.res.get("webjar:/foundation-icon-fonts/foundation-icons.woff", "css/foundation-icons.woff")
        case None =>
          logger.warn(s"Page ${pc.page.uri}: Foundation icon ${n.destinationURI} not found")
      }
    }
  }

  override def renderAttributedHeading(n: AttributedHeading, c: HtmlNodeRendererContext): Unit = if(n.id ne null) {
    val wr = c.getWriter
    val htag = s"h${n.getLevel}"
    wr.line
    val attrs = Map[String, String]("id" -> n.id, "class" -> "a_section", "data-magellan-target" -> n.id)
    wr.tag(htag, c.extendAttributes(n, attrs.asJava))
    n.children.toVector.foreach(c.render)
    wr.raw(s"""<a class="a_hlink" href="#${n.id}"></a>""")
    wr.tag('/' + htag)
    wr.line
  } else super.renderAttributedHeading(n, c)

  override def renderMermaid(n: AttributedFencedCodeBlock, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = {
    pc.res.get(mermaidJS, "js/" + mermaidJS.split('/').last, createLink = true)
    pc.res.get("mermaid.custom.css", "css/", createLink = true)
    val wr = c.getWriter
    wr.tag("div", Map("class" -> "mermaid", "id" -> pc.newID()).asJava)
    wr.tag("pre", Map("class" -> "mermaid_src", "style" -> "display: none").asJava)
    wr.text(n.getLiteral)
    wr.tag("/pre")
    wr.tag("/div")
    pc.requireJavaScript()
  }

  override def renderCode(hlr: HighlightResult, code: Node, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = if(code.isInstanceOf[Block]) {
    val wr = c.getWriter
    wr.line
    wr.raw("<div class=\"row\"><div class=\"a_linked small-expand columns a_xscroll a_codeblock\">")
    super.renderCode(hlr, code, c, pc)
    code match {
      case attr: Attributed =>
        attr.defAttrs.get("sourceLinkURI").foreach { uri =>
          val text = attr.defAttrs.get("sourceLinkText").getOrElse(generateSourceLinkText(uri))
          wr.tag("a", Map("href" -> uri, "class" -> "a_sourcelink").asJava)
          wr.text(text)
          wr.tag("/a")
        }
      case _ =>
    }
    wr.raw("</div></div>")
    wr.line
  } else super.renderCode(hlr, code, c, pc)

  def generateSourceLinkText(uri: String): String = try {
    val p = new URI(uri).getPath
    val fname = URLDecoder.decode(p.substring(p.lastIndexOf('/')+1), "UTF-8")
    if(fname.nonEmpty) fname else uri
  } catch { case ex: Exception =>
    logger.warn("Error generating source link text for URI: "+uri, ex)
    uri
  }

  override def renderTabView(pc: HtmlPageContext)(n: TabView, c: HtmlNodeRendererContext): Unit = {
    val id = pc.newID()
    val items = n.children.collect { case i: TabItem => (i, pc.newID()) }.zipWithIndex.toVector
    val wr = c.getWriter
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

  def renderTableBlock(n: TableBlock, c: HtmlNodeRendererContext): Unit = {
    val wr = c.getWriter
    wr.line
    wr.raw("""<div class="row"><div class="small-expand columns a_xscroll a_table">""")
    wr.tag("table", c.extendAttributes(n, Collections.emptyMap[String, String]))
    n.children.toVector.foreach(c.render)
    wr.tag("/table")
    wr.raw("</div></div>")
    wr.line()
  }

  override def renderers(pc: HtmlPageContext): Seq[HtmlNodeRendererFactory] =
    SimpleHtmlNodeRenderer(renderTableBlock _) +: super.renderers(pc)

  override def createPageModel(pc: HtmlPageContext, renderer: HtmlRenderer): HtmlPageModel =
    new DefaultPageModel(pc, renderer)
}

class DefaultPageModel(pc: HtmlPageContext, renderer: HtmlRenderer) extends HtmlPageModel(pc, renderer) {
  protected def navBar(opt: String): Option[Seq[NavLink]] = pc.themeConfigStringList(opt).flatMap { defined =>
    val links = defined.flatMap(id => navLinks.get(id).filter(_.hasText))
    if(links.exists(_.target.isDefined)) {
      links.foreach(_.text) // force rendering
      Some(links)
    } else None
  }

  // These are non-lazy vals to force rendering (which may request additional resources) before HEAD
  val topNavBar = navBar("topNavBar")
  val bottomNavBar = navBar("bottomNavBar")
}
