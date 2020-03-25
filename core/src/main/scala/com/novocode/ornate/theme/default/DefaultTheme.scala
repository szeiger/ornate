package com.novocode.ornate.theme.default

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.Collections

import com.novocode.ornate.commonmark._
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.commonmark.HtmlNodeRendererContextExtensionMethods._
import com.novocode.ornate.config.Global
import com.novocode.ornate.highlight.HighlightResult
import com.novocode.ornate._
import com.novocode.ornate.js.WebJarSupport
import com.novocode.ornate.theme._
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.renderer.html.{HtmlNodeRendererContext, HtmlNodeRendererFactory, HtmlRenderer}
import org.commonmark.node.{Block, Document, Node}

import scala.jdk.CollectionConverters._
import scala.io.Codec

class DefaultTheme(global: Global) extends HtmlTheme(global) {
  lazy val fontAwesome: Map[String, String] = try {
    val url = resolveResource(new URI("webjar:/font-awesome/scss/_variables.scss")).toURL
    val P = """\$fa-var-(.*): "\\(....)";""".r
    Util.readLines(url).collect { case P(name, code) => (name, Integer.parseInt(code, 16).toChar.toString) }.toMap
  } catch { case ex: Exception =>
    logger.error("Error creating Font Awesome mapping", ex)
    Map.empty
  }

  override def synthesizePages(missingSyntheticPages: Vector[(String, URI)]): Vector[Page] = {
    missingSyntheticPages.flatMap {
      case ("toc", u) =>
        logger.debug(s"Creating TOC page $u")
        Some(PageParser.parseContent(None, Some("toc"), global.userConfig, u, ".md",
          Some(global.userConfig.theme.config.getString("strings.tocPage")),
          global.userConfig.raw))
      case ("index", u) =>
        logger.debug(s"Creating index page $u")
        Some(PageParser.parseContent(None, Some("index"), global.userConfig, u, ".md",
          Some(global.userConfig.theme.config.getString("strings.indexPage")),
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

  override def specialImageSchemesInline: Set[String] = Set("foundation-icon", "font-awesome", "versions")

  override def renderSpecialImageInline(pc: HtmlPageContext)(n: SpecialImageInline, c: HtmlNodeRendererContext): Unit = n.destinationURI.getScheme match {
    case "foundation-icon" =>
      val name = n.destinationURI.getSchemeSpecificPart
      FoundationIcons.icons.get(name) match {
        case Some(glyph) =>
          c.getWriter.raw(s"""<span class="a_foundation_icon">$glyph</span>""")
          pc.res.get("foundation-icons.custom.css", "css/", createLink = true)
          pc.res.get("webjar:/foundation-icon-fonts/foundation-icons.woff", "css/foundation-icons.woff")
        case None =>
          logger.warn(s"Page ${pc.page.uri}: Foundation icon ${n.destinationURI} not found")
      }
    case "font-awesome" =>
      val name = n.destinationURI.getSchemeSpecificPart
      fontAwesome.get(name) match {
        case Some(glyph) =>
          c.getWriter.raw(s"""<span class="a_fontawesome">$glyph</span>""")
          pc.res.get("fontawesome.custom.css", "css/", createLink = true)
          pc.res.get("webjar:/font-awesome/fonts/fontawesome-webfont.woff", "css/fontawesome-webfont.woff")
        case None =>
          logger.warn(s"Page ${pc.page.uri}: Font Awesome icon ${n.destinationURI} not found")
      }
    case "versions" =>
      if(tc.getBoolean("versionNavDropdown")) {
        pc.features.request(DefaultTheme.MultiVersion)
        val wr = c.getWriter
        val id = pc.newID()
        wr.raw(s"""<a class="a_vnav2" data-toggle="$id">""")
        c.renderChildren(n)
        wr.raw(s"""</a><span class="dropdown-pane a_vnav2_pane" id="$id"><span>""")
        val loading = pc.stringNode("versionNavLoading").get
        c.renderInline(loading)
        wr.raw("</span></span>")
      } else c.renderChildren(n)
    case _ =>
  }

  override def renderAttributedHeading(pc: HtmlPageContext)(n: AttributedHeading, c: HtmlNodeRendererContext): Unit = if(n.id ne null) {
    val wr = c.getWriter
    val htag = s"h${n.getLevel}"
    wr.line
    val baseAttrs = Map[String, String]("id" -> n.id, "class" -> "a_section")
    val attrs =
      if(n.getLevel <= pc.asInstanceOf[DefaultPageContext].pageNavMaxLevel) baseAttrs + ("data-magellan-target" -> n.id)
      else baseAttrs
    wr.tag(htag, c.extendAttributes(n, attrs.asJava))
    c.renderChildren(n)
    wr.raw(s"""<a class="a_hlink" href="#${n.id}"></a>""")
    wr.tag('/' + htag)
    wr.line
  } else super.renderAttributedHeading(pc)(n, c)

  override def renderMermaid(n: AttributedFencedCodeBlock, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = {
    pc.features.request(HtmlFeatures.JavaScript)
    pc.res.get(mermaidJS, "js/" + mermaidJS.split('/').last, createLink = true)
    pc.res.get("mermaid.custom.css", "css/", createLink = true)
    val wr = c.getWriter
    wr.tag("div", Map("class" -> "mermaid", "id" -> pc.newID()).asJava)
    wr.tag("pre", Map("class" -> "mermaid_src", "style" -> "display: none").asJava)
    wr.text(n.getLiteral)
    wr.tag("/pre")
    wr.tag("/div")
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
      c.renderChildren(item)
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
    c.renderChildren(n)
    wr.tag("/table")
    wr.raw("</div></div>")
    wr.line()
  }

  override def renderers(pc: HtmlPageContext): Seq[HtmlNodeRendererFactory] =
    SimpleHtmlNodeRenderer(renderTableBlock _) +: super.renderers(pc)

  override def createPageContext(siteContext: HtmlSiteContext, page: Page): DefaultPageContext =
    new DefaultPageContext(siteContext, page)

  override def createPageModel(pc: HtmlPageContext, renderer: HtmlRenderer): DefaultPageModel =
    new DefaultPageModel(pc.asInstanceOf[DefaultPageContext], renderer)

  override def createSiteModel(pms: Vector[HtmlPageModel]): HtmlSiteModel = new DefaultSiteModel(this, pms)
}

object DefaultTheme {
  case object MultiVersion extends HtmlFeatures.Feature
}

class DefaultPageContext(siteContext: HtmlSiteContext, page: Page) extends HtmlPageContext(siteContext, page) {
  lazy val pageNavMaxLevel: Int = themeConfigInt("pageNavMaxLevel").getOrElse(siteContext.theme.global.userConfig.tocMaxLevel)
}

class DefaultPageModel(override val pc: DefaultPageContext, renderer: HtmlRenderer) extends HtmlPageModel(pc, renderer) {
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

  if(pc.themeConfigBoolean("versionNav").getOrElse(false)) pc.features.request(DefaultTheme.MultiVersion)

  def versionIdxLink: Option[URI] = pc.themeConfig("versionIndex").map(s => siteRootLink.resolve(s))
  def siteRootLink: URI = Util.relativeSiteURI(pc.page.uri, Util.siteRootURI)
}

class DefaultSiteModel(theme: DefaultTheme, pms: Vector[HtmlPageModel]) extends HtmlSiteModel(theme, pms) {
  def themeConfigColor(path: String): Option[Color] = themeConfig(path).flatMap { s =>
    try Some(Color.parse(s)) catch {
      case ex: Exception =>
        theme.logger.error("Error parsing color from theme config key "+path, ex)
        None
    }
  }

  lazy val accentColor: Color = themeConfigColor("global.color.accent").get
  lazy val accentBackground: Color =
    themeConfigColor("global.color.accentBackground").getOrElse(accentColor.withAlpha(accentColor.alpha * 0.15f))
  lazy val headerFooterColor: Color = themeConfigColor("global.color.headerFooter").get
  lazy val headerFooterBackground: Color = themeConfigColor("global.color.headerFooterBackground").get

  lazy val extraCSS: String = {
    val css1 = themeConfig("global.cssFile").map { path =>
      val f = theme.global.getFile(path)
      try {
        val s = f.contentAsString(StandardCharsets.UTF_8)
        if(s.nonEmpty && !s.endsWith("\n")) s + "\n" else s
      } catch { case ex: Exception =>
        theme.logger.error("Error loading CSS file \""+path+"\" (resolved to \""+f+"\")", ex)
      }
    }.getOrElse("")
    val css2 = themeConfig("global.css").map { s =>
      if(s.nonEmpty && !s.endsWith("\n")) s + "\n" else s
    }.getOrElse("")
    css1 + css2
  }
}
