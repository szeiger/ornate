package com.novocode.ornate.theme

import java.net.{URL, URI}
import java.util.Collections

import better.files.File.OpenOptions
import com.novocode.ornate._
import com.novocode.ornate.commonmark.NodeExtensionMethods._

import better.files._
import com.novocode.ornate.commonmark._
import com.novocode.ornate.config.Global
import com.novocode.ornate.highlight.{HighlightResult, HighlightTarget}
import com.novocode.ornate.js.ElasticlunrSearch
import org.commonmark.html.HtmlRenderer
import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.html.renderer.{NodeRendererFactory, NodeRendererContext, NodeRenderer}
import org.commonmark.node._
import play.twirl.api.{Html, Template1, HtmlFormat}

import scala.StringBuilder
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Codec

/** Base class for Twirl-based HTML themes */
class HtmlTheme(global: Global) extends Theme(global) { self =>
  import HtmlTheme._
  val tc = global.userConfig.theme.config
  val suffix = ".html"
  val indexPage = if(tc.hasPath("global.indexPage")) Some(tc.getString("global.indexPage")) else None

  class PageContext(val page: Page, val slp: SpecialLinkProcessor) {
    private[this] var last = -1
    def newID(): String = {
      last += 1
      s"_id$last"
    }
  }

  def targetFile(uri: URI, base: File): File =
    uri.getPath.split('/').filter(_.nonEmpty).foldLeft(base) { case (f, s) => f / s }

  /** Render a heading with an ID. It can be overridden in subclasses as needed. */
  def renderAttributedHeading(n: AttributedHeading, c: NodeRendererContext): Unit = {
    val htag = s"h${n.getLevel}"
    val attrs = c.extendAttributes(n, Collections.emptyMap[String, String])
    if(n.id ne null) attrs.put("id", n.id)
    val classes = n.simpleAttrs.filter(_.startsWith(".")).map(_.drop(1))
    if(classes.nonEmpty) attrs.put("class", classes.mkString(" "))
    val wr = c.getHtmlWriter
    wr.line
    wr.tag(htag, attrs)
    n.children.toVector.foreach(c.render)
    wr.tag('/' + htag)
    wr.line
  }

  def renderAttributedBlockQuote(n: AttributedBlockQuote, c: NodeRendererContext): Unit = {
    val attrs = c.extendAttributes(n, Collections.emptyMap[String, String])
    if(n.id ne null) attrs.put("id", n.id)
    val classes = n.simpleAttrs.filter(_.startsWith(".")).map(_.drop(1))
    if(classes.nonEmpty) attrs.put("class", classes.mkString(" "))
    val wr = c.getHtmlWriter
    wr.line
    wr.tag("blockquote", attrs)
    wr.line
    n.children.toVector.foreach(c.render)
    wr.line
    wr.tag("/blockquote")
    wr.line
  }

  /** Render a tab view. The default implementation simply renders the content so that merged code blocks
    * look no different than regular code blocks. Themes can override this method to render the actual
    * tab view. */
  def renderTabView(pc: PageContext)(n: TabView, c: NodeRendererContext): Unit = {
    n.children.toVector.foreach {
      case i: TabItem =>
        i.children.toVector.foreach(c.render)
      case n => c.render(n)
    }
  }

  /** Render code that was run through the highlighter. This method is called for all fenced code blocks,
    * indented code blocks and inline code. It can be overridden in subclasses as needed. */
  def renderCode(hlr: HighlightResult, c: NodeRendererContext, block: Boolean): Unit = {
    val langCode = hlr.language.map("language-" + _)
    val codeClasses = (if(block) hlr.preCodeClasses else hlr.codeClasses) ++ langCode
    val codeAttrs: Map[String, String] = (if(codeClasses.nonEmpty) Map("class" -> codeClasses.mkString(" ")) else Map.empty)
    val preAttrs: Map[String, String] = (if(hlr.preClasses.nonEmpty) Map("class" -> hlr.preClasses.mkString(" ")) else Map.empty)
    val wr = c.getHtmlWriter
    if(block) {
      wr.line
      wr.tag("pre", preAttrs.asJava)
    }
    wr.tag("code", codeAttrs.asJava)
    wr.raw(hlr.html.toString)
    wr.tag("/code")
    if(block) {
      wr.tag("/pre")
      wr.line
    }
  }

  def fencedCodeBlockRenderer(page: Page, css: ThemeResources) = SimpleHtmlNodeRenderer { (n: AttributedFencedCodeBlock, c: NodeRendererContext) =>
    val info = if(n.getInfo eq null) Vector.empty else n.getInfo.split(' ').filter(_.nonEmpty).toVector
    val lang = info.headOption
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, lang, HighlightTarget.FencedCodeBlock, page)
    hlr.css.foreach(u => css.getURI(u, null, u.getPath.endsWith(".css")))
    renderCode(hlr.copy(language = lang.orElse(hlr.language)), c, true)
  }

  def indentedCodeBlockRenderer(page: Page, css: ThemeResources) = SimpleHtmlNodeRenderer { (n: IndentedCodeBlock, c: NodeRendererContext) =>
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, None, HighlightTarget.IndentedCodeBlock, page)
    hlr.css.foreach(u => css.getURI(u, null, u.getPath.endsWith(".css")))
    renderCode(hlr, c, true)
  }

  def inlineCodeRenderer(page: Page, css: ThemeResources) = SimpleHtmlNodeRenderer { (n: Code, c: NodeRendererContext) =>
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, None, HighlightTarget.InlineCode, page)
    hlr.css.foreach(u => css.getURI(u, null, u.getPath.endsWith(".css")))
    renderCode(hlr, c, false)
  }

  def emojiRenderer(pc: PageContext) = SimpleHtmlNodeRenderer { (n: Emoji, c: NodeRendererContext) =>
    val wr = c.getHtmlWriter
    if(n.uri ne null) {
      wr.raw(s"""<img class="emoji" title="${n.name}" alt="""")
      wr.text(n.unicode)
      wr.raw("""" src="""")
      wr.text(pc.slp.resolve(pc.page.uri, n.uri.toString, "image", false, true))
      wr.raw("""""/>""")
    } else {
      wr.raw(s"""<span class="emoji" title="${n.name}">""")
      wr.text(n.unicode)
      wr.raw("</span>")
    }
  }

  class ThemeResources(val page: Page, tpe: String) extends Resources {
    private[this] val baseURI = {
      val dir = tc.getString(s"global.dirs.$tpe")
      Util.siteRootURI.resolve(if(dir.endsWith("/")) dir else dir + "/")
    }
    private[this] val buf = new mutable.ArrayBuffer[ResourceSpec]
    private[this] val map = new mutable.HashMap[URL, ResourceSpec]

    def getURI(sourceURI: URI, targetFile: String, keepLink: Boolean): URI = {
      try {
        val url = resolveResource(sourceURI)
        map.getOrElseUpdate(url, {
          val targetURI =
            if(sourceURI.getScheme == "site") sourceURI // link to site resources at their original location
            else {
              val tname = (if(targetFile eq null) suggestRelativePath(sourceURI, tpe) else targetFile).replaceAll("^/*", "")
              baseURI.resolve(tname)
            }
          val spec = ResourceSpec(sourceURI, url, targetURI, keepLink)
          buf += spec
          spec
        }).uri
      } catch { case ex: Exception =>
        logger.error(s"Error resolving theme resource URI $sourceURI -- Skipping resource and using original link")
        sourceURI
      }
    }
    def mappings: Iterable[ResourceSpec] = buf
  }

  class PageModelImpl(p: Page, site: Site, slp: SpecialLinkProcessor,
                      val renderer: HtmlRenderer, val css: ThemeResources, val image: ThemeResources) extends PageModel {
    def theme = self
    val title = HtmlFormat.escape(p.section.title.getOrElse(""))
    val content = HtmlFormat.raw(renderer.render(p.doc))
    val js = new ThemeResources(p, "js")
    private lazy val pageTC = global.userConfig.theme.getConfig(p.config)
    def pageConfig(path: String): Option[String] =
      if(p.config.hasPath(path)) Some(p.config.getString(path)) else None
    def themeConfig(path: String): Option[String] =
      if(pageTC.hasPath(path)) Some(pageTC.getString(path)) else None
    def themeConfigBoolean(path: String): Option[Boolean] =
      if(pageTC.hasPath(path)) Some(pageTC.getBoolean(path)) else None
    def sections: Vector[Section] = p.section.children
    lazy val siteNav: Option[Vector[ExpandTocProcessor.TocItem]] = themeConfig("siteNav") match {
      case Some(uri) =>
        val tocBlock = SpecialImageProcessor.parseTocURI(uri, global.userConfig)
        Some(ExpandTocProcessor.buildTocTree(tocBlock, site.toc, p))
      case None => None
    }
    def resolveLink(dest: String): String = slp.resolve(p.uri, dest, "link", true, false)
    private def string(name: String): Option[Node] = themeConfig(s"strings.$name").map { md =>
      val snippet = p.parseAndProcessSnippet(md)
      slp(snippet)
      snippet.doc
    }
    def stringHtml(name: String): Option[Html] = string(name).map(n => HtmlFormat.raw(renderer.render(n)))
    def stringText(name: String): Option[Html] = string(name).map(n => HtmlFormat.escape(NodeUtil.extractText(n)))
    def searchLink: Option[String] = {
      val searchPage =
        if(tc.hasPath("global.pages.search")) Some(tc.getString("global.pages.search")) else None
      searchPage.map { uri =>
        Util.rewriteIndexPageLink(Util.relativeSiteURI(p.uri, Util.replaceSuffix(Util.siteRootURI.resolve(uri), ".md", ".html")), indexPage).toString
      }
    }
    def searchIndex: Option[String] = {
      val indexFile =
        if(tc.hasPath("global.searchIndex")) Some(tc.getString("global.searchIndex")) else None
      indexFile.map { uri =>
        Util.relativeSiteURI(p.uri, Util.siteRootURI.resolve(uri)).toString
      }
    }
  }

  def renderers(pc: PageContext): Seq[NodeRendererFactory] = Seq(
    emojiRenderer(pc),
    SimpleHtmlNodeRenderer(renderAttributedBlockQuote _),
    SimpleHtmlNodeRenderer(renderAttributedHeading _),
    SimpleHtmlNodeRenderer(renderTabView(pc) _)
  )

  def render(site: Site): Unit = {
    val staticResources = global.findStaticResources
    val staticResourceURIs = staticResources.iterator.map(_._2.getPath).toSet
    val siteResources = new mutable.HashMap[URL, ResourceSpec]

    site.pages.foreach { p =>
      val file = targetFile(p.uriWithSuffix(suffix), global.userConfig.targetDir)
      try {
        val templateName = global.userConfig.theme.getConfig(p.config).getString("template")
        logger.debug(s"Rendering page ${p.uri} to file $file with template ${templateName}")
        val imageRes = new ThemeResources(p, "image")
        val cssRes = new ThemeResources(p, "css")
        val slp = new SpecialLinkProcessor(imageRes, site, suffix, indexPage, staticResourceURIs)
        slp(p)
        val pc = new PageContext(p, slp)
        val template = getTemplate(templateName)
        val renderer = renderers(pc).foldLeft(HtmlRenderer.builder()) { case (z, n) => z.nodeRendererFactory(n) }
          .nodeRendererFactory(fencedCodeBlockRenderer(p, cssRes))
          .nodeRendererFactory(indentedCodeBlockRenderer(p, cssRes))
          .nodeRendererFactory(inlineCodeRenderer(p, cssRes))
          .extensions(p.extensions.htmlRenderer.asJava).build()
        val pm = new PageModelImpl(p, site, slp, renderer, cssRes, imageRes)
        val formatted = template.render(pm).body.trim
        siteResources ++= (pm.css.mappings ++ pm.js.mappings ++ pm.image.mappings).map(r => (r.url, r))
        file.parent.createDirectories()
        file.write(formatted+'\n')(codec = Codec.UTF8)
      } catch { case ex: Exception =>
        logger.error(s"Error rendering page ${p.uri} to $file", ex)
      }
    }

    try createSearchIndex(site)
    catch { case ex: Exception => logger.error(s"Error creating search index", ex) }

    staticResources.foreach { case (sourceFile, uri) =>
      val file = targetFile(uri, global.userConfig.targetDir)
      logger.debug(s"Copying static resource $uri to file $file")
      try sourceFile.copyTo(file, overwrite = true)
      catch { case ex: Exception =>
        logger.error(s"Error copying static resource file $sourceFile to $file", ex)
      }
    }

    siteResources.valuesIterator.filter(_.sourceURI.getScheme != "site").foreach { rs =>
      val file = targetFile(rs.uri, global.userConfig.targetDir)
      logger.debug(s"Copying theme resource ${rs.url} to file $file")
      try {
        file.parent.createDirectories()
        val in = rs.url.openStream()
        try {
          val out = file.newOutputStream
          try in.pipeTo(out) finally out.close
        } finally in.close
      } catch { case ex: Exception =>
        logger.error(s"Error copying theme resource ${rs.url} to $file", ex)
      }
    }
  }

  protected def createSearchIndex(site: Site): Unit = {
    if(tc.hasPath("global.pages.search") && tc.hasPath("global.searchIndex")) {
      val searchPage = Util.siteRootURI.resolve(tc.getString("global.pages.search"))
      val searchIndexFile = targetFile(Util.siteRootURI.resolve(tc.getString("global.searchIndex")), global.userConfig.targetDir)
      logger.debug("Writing search index to "+searchIndexFile)
      val exclude = new FileMatcher(
        if(tc.hasPath("global.searchExcludePages")) tc.getStringList("global.searchExcludePages").asScala.toVector else Vector.empty
      )
      val excerptLength = if(tc.hasPath("global.searchExcerptLength")) tc.getInt("global.searchExcerptLength") else 0
      val idx = ElasticlunrSearch.createIndex
      site.pages.foreach { p =>
        if(exclude.matchesPath(p.uri.getPath))
          logger.debug(s"Excluding page ${p.uri} from search index")
        else {
          val body = NodeUtil.extractText(p.doc).trim
          val title = p.section.title.getOrElse("").trim
          val excerpt = if(excerptLength > 0) {
            val bodyOnly = NodeUtil.extractText(p.doc, withCodeBlocks=false, withFirstHeading=false, limit=excerptLength).trim
            val short = bodyOnly.indexOf(' ', excerptLength) match {
              case -1 => bodyOnly
              case n => bodyOnly.substring(0, n)
            }
            val dots = short.reverseIterator.takeWhile(_ == '.').length
            val noDots = if(dots == 0) short else short.substring(0, short.length-dots)
            noDots + "..."
          } else ""
          val link = Util.rewriteIndexPageLink(Util.relativeSiteURI(searchPage, p.uriWithSuffix(suffix)), indexPage).toString
          val keywords =
            if(p.config.hasPath("meta.keywords")) p.config.getString("meta.keywords") else ""
          idx.add(title, body, excerpt, keywords, link)
        }
      }
      implicit val codec = Codec.UTF8
      searchIndexFile.write("window._searchIndex = "+idx.toJSON+";")
    }
  }

  private[this] val templateBase = getClass.getName.replaceAll("\\.[^\\.]*$", "")
  private[this] val templates = new mutable.HashMap[String, Template]
  def getTemplate(name: String) = templates.getOrElseUpdate(name, {
    val className = s"$templateBase.$name"
    logger.debug("Creating template from class "+className)
    Class.forName(className).newInstance().asInstanceOf[Template]
  })
}

object HtmlTheme {
  type Template = Template1[PageModel, HtmlFormat.Appendable]

  trait PageModel {
    def renderer: HtmlRenderer
    def theme: HtmlTheme
    def title: Html
    def content: Html
    def css: Resources
    def js: Resources
    def image: Resources
    def pageConfig(path: String): Option[String]
    def themeConfig(path: String): Option[String]
    def themeConfigBoolean(path: String): Option[Boolean]
    def sections: Vector[Section]
    def siteNav: Option[Vector[ExpandTocProcessor.TocItem]]
    def resolveLink(dest: String): String
    def stringHtml(name: String): Option[Html]
    def stringText(name: String): Option[Html]
    def searchLink: Option[String]
    def searchIndex: Option[String]
  }
}
