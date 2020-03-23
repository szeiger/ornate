package com.novocode.ornate.theme

import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.{Collections, Comparator, Locale}

import better.files.File.OpenOptions
import com.novocode.ornate._
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.commonmark.HtmlNodeRendererContextExtensionMethods._
import better.files._
import com.novocode.ornate.URIExtensionMethods._
import com.novocode.ornate.commonmark._
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.novocode.ornate.config.Global
import com.novocode.ornate.highlight.{HighlightResult, Highlit, HighlitBlock, HighlitInline}
import com.novocode.ornate.js.{CSSO, ElasticlunrSearch, WebJarSupport}
import com.typesafe.config.{ConfigObject, ConfigRenderOptions, ConfigValue}
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.renderer.html.{HtmlNodeRendererContext, HtmlNodeRendererFactory}
import org.commonmark.node._
import play.twirl.api.{Html, HtmlFormat, Template1, TxtFormat}

import scala.StringBuilder
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.Codec

/** Base class for Twirl-based HTML themes */
class HtmlTheme(global: Global) extends Theme(global) { self =>
  val mathJaxExclude = new FileMatcher(Seq(
    "/config/local/", "/docs/", "/test/", "/unpacked/", "*.md", "*.html", "*.txt", "*.json", ".*"
  ))
  val mermaidJS = "classpath:/com/novocode/ornate/theme/mermaidAPI-0.5.8.min.js"

  val tc = global.userConfig.theme.config
  val suffix = ".html"
  val indexPage = tc.getStringOpt("global.indexPage")
  val minifyCSS = tc.getBooleanOr("global.minify.css")
  val minifyJS = tc.getBooleanOr("global.minify.js")
  val minifyHTML = tc.getBooleanOr("global.minify.html")

  def targetDir: File = global.userConfig.targetDir

  def targetFile(uri: URI): File =
    uri.getPath.split('/').filter(_.nonEmpty).foldLeft(targetDir) { case (f, s) => f / s }

  /** Render a heading with an ID. It can be overridden in subclasses as needed. */
  def renderAttributedHeading(pc: HtmlPageContext)(n: AttributedHeading, c: HtmlNodeRendererContext): Unit = {
    val htag = s"h${n.getLevel}"
    val attrs = c.extendAttributes(n, Collections.emptyMap[String, String])
    if(n.id ne null) attrs.put("id", n.id)
    val classes = n.simpleAttrs.filter(_.startsWith(".")).map(_.drop(1))
    if(classes.nonEmpty) attrs.put("class", classes.mkString(" "))
    val wr = c.getWriter
    wr.line
    wr.tag(htag, attrs)
    c.renderChildren(n)
    wr.tag('/'.toString + htag)
    wr.line
  }

  def renderAttributedBlockQuote(n: AttributedBlockQuote, c: HtmlNodeRendererContext): Unit = {
    val attrs = c.extendAttributes(n, Collections.emptyMap[String, String])
    if(n.id ne null) attrs.put("id", n.id)
    val classes = n.simpleAttrs.filter(_.startsWith(".")).map(_.drop(1))
    if(classes.nonEmpty) attrs.put("class", classes.mkString(" "))
    val wr = c.getWriter
    wr.line
    wr.tag("blockquote", attrs)
    wr.line
    c.renderChildren(n)
    wr.line
    wr.tag("/blockquote")
    wr.line
  }

  /** Render a tab view. The default implementation simply renders the content so that merged code blocks
    * look no different than regular code blocks. Themes can override this method to render the actual
    * tab view. */
  def renderTabView(pc: HtmlPageContext)(n: TabView, c: HtmlNodeRendererContext): Unit = {
    n.children.toVector.foreach {
      case i: TabItem => c.renderChildren(i)
      case n => c.render(n)
    }
  }

  /** Render code that was run through the highlighter. This method is called for all fenced code blocks,
    * indented code blocks and inline code. It can be overridden in subclasses as needed. */
  def renderCode(hlr: HighlightResult, code: Node, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = {
    val block = code.isInstanceOf[Block]
    val langCode = hlr.language.map("language-" + _)
    val codeClasses = (if(block) hlr.preCodeClasses else hlr.codeClasses) ++ langCode
    val codeAttrs: Map[String, String] = (if(codeClasses.nonEmpty) Map("class" -> codeClasses.mkString(" ")) else Map.empty)
    val preAttrs: Map[String, String] = (if(hlr.preClasses.nonEmpty) Map("class" -> hlr.preClasses.mkString(" ")) else Map.empty)
    val wr = c.getWriter
    hlr.css.foreach(u => pc.res.getURI(u, null, u.getPath.endsWith(".css"), false))
    if(block) {
      wr.line
      wr.tag("pre", preAttrs.asJava)
    }
    wr.tag("code", codeAttrs.asJava)
    code match {
      case n: AttributedFencedCodeBlock if n.postHighlightSubstitutions.nonEmpty =>
        val ch = n.children.toVector
        n.splitProcessed(hlr.html.toString).foreach {
          case Left(s) => wr.raw(s)
          case Right(idx) =>
            c.render(ch(idx))
        }
      case _ =>
        wr.raw(hlr.html.toString)
    }
    wr.tag("/code")
    if(block) {
      wr.tag("/pre")
      wr.line
    }
  }

  val defaultNoHighlightLanguages: Set[String] = Set("mermaid", "texmath", "asciimath", "math", "mathml")

  override def noHighlightLanguages(p: Page): Set[String] = defaultNoHighlightLanguages

  def renderFencedCodeBlock(pc: HtmlPageContext)(n: AttributedFencedCodeBlock, c: HtmlNodeRendererContext): Unit = n.getLanguage match {
    case Some("mermaid") => renderMermaid(n, c, pc)
    case Some("texmath") => renderMath(n.getLiteral, c, pc, "tex", true)
    case Some("asciimath" | "math") => renderMath(n.getLiteral, c, pc, "asciimath", true)
    case Some("mathml") => renderMath(n.getLiteral, c, pc, "mml", true)
    case lang => renderCode(HighlightResult.simple(n.getLiteral, lang), n, c, pc)
  }

  def renderIndentedCodeBlock(pc: HtmlPageContext)(n: IndentedCodeBlock, c: HtmlNodeRendererContext): Unit =
    renderCode(HighlightResult.simple(n.getLiteral, None), n, c, pc)

  def renderInlineCode(pc: HtmlPageContext)(n: Code, c: HtmlNodeRendererContext): Unit =
    renderCode(HighlightResult.simple(n.getLiteral, None), n, c, pc)

  def renderHighlitBlock(pc: HtmlPageContext)(n: HighlitBlock, c: HtmlNodeRendererContext): Unit =
    renderCode(n.result, n.getFirstChild, c, pc)

  def renderHighlitInline(pc: HtmlPageContext)(n: HighlitInline, c: HtmlNodeRendererContext): Unit =
    renderCode(n.result, n.getFirstChild, c, pc)

  /** This method should be overridden if `specialImageSchemesInline` is used. */
  def renderSpecialImageInline(pc: HtmlPageContext)(n: SpecialImageInline, c: HtmlNodeRendererContext): Unit = ()

  /** This method should be overridden if `specialImageSchemesBlock` is used. */
  def renderSpecialImageBlock(pc: HtmlPageContext)(n: SpecialImageBlock, c: HtmlNodeRendererContext): Unit = ()

  def renderIndexBlock(pc: HtmlPageContext)(n: IndexBlock, c: HtmlNodeRendererContext): Unit = {
    val locale = Locale.US
    val wr = c.getWriter
    val grouped = n.index.groupBy(s => s.text.toUpperCase(locale).codePointAt(0)).toVector
    val (alphanumeric, symbolic) = grouped.partition { case (cp, _) => Character.isAlphabetic(cp) || Character.isDigit(cp) }

    val coll = Collator.getInstance(locale)
    coll.setStrength(Collator.PRIMARY)
    val collOrd: Ordering[String] = Ordering.comparatorToOrdering(coll.asInstanceOf[Comparator[String]])

    def cpid(s: String): String = s.codePoints().iterator().asScala.map { cp =>
      if((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) String.valueOf(cp.toChar)
      else if(cp >= '0' && cp <= '9') "d" + String.valueOf(cp.toChar)
      else "u" + Integer.toHexString(cp)
    }.mkString

    val groups =
      alphanumeric.map { case (cp, v) =>
        val s = new String(Array(cp), 0, 1).toUpperCase(locale)
        (s, cpid(s), v)
      }.sortBy(_._1)(collOrd) ++
        (if(symbolic.isEmpty) Vector.empty else Vector((null, "symbols", symbolic.flatMap(_._2))))

    def render(e: IndexBlock.IndexEntry): Unit = {
      wr.tag("li")
      val resolved = e.destinations.map(u => pc.resolveLink(u.toString))
      if(resolved.isEmpty) {
        wr.text(e.text)
      } else resolved.zipWithIndex.foreach { case (r, idx) =>
        if(idx > 0) wr.text(", ")
        wr.tag("a", Map("href" -> r).asJava)
        wr.text(if(idx == 0) e.text else "["+idx.toString+"]")
        wr.tag("/a")
      }
      if(e.children.nonEmpty) {
        wr.tag("ul")
        e.children.foreach(render)
        wr.tag("/ul")
      }
      wr.tag("/li")
    }

    def renderTitle(title: String): Unit = {
      if(title eq null) {
        val n = pc.stringNode("indexSymbols").get
        def render(n: Node): Unit =
          if(n.isInstanceOf[Block]) n.children.foreach(render) else c.render(n)
        render(n)
      } else wr.text(title)
    }

    groups.zipWithIndex.foreach { case ((title, id, entries), idx) =>
      if(idx > 0) wr.raw(" | ")
      wr.tag("a", Map("href" -> s"#$id").asJava)
      renderTitle(title)
      wr.tag("/a")
    }

    groups.foreach { case (title, id, entries) =>
      wr.tag("h2", Map("id" -> id).asJava)
      renderTitle(title)
      wr.tag("/h2")
      wr.tag("ul")
      entries.foreach(render)
      wr.tag("/ul")
    }
  }

  /** Render a Mermaid diagram block. This does not add any dependency on Mermaid to the generated site.
    * The method should be overwritten accordingly (unless a theme always adds it anyway). */
  def renderMermaid(n: AttributedFencedCodeBlock, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = {
    pc.features.request(HtmlFeatures.Mermaid)
    pc.features.request(HtmlFeatures.JavaScript)
    val wr = c.getWriter
    wr.tag("div", Map("class" -> "mermaid", "id" -> pc.newID()).asJava)
    wr.text(n.getLiteral)
    wr.tag("/div")
  }

  def renderInlineMath(pc: HtmlPageContext)(n: InlineMath, c: HtmlNodeRendererContext): Unit =
    renderMath(n.literal, c, pc, n.language, false)

  def renderMathBlock(pc: HtmlPageContext)(n: MathBlock, c: HtmlNodeRendererContext): Unit =
    renderMath(n.literal, c, pc, n.language, true)

  /** Render a TeX math, MML or ASCIIMath block or inline element. The default implementation puts the code into a
    * "script" element with the proper language code (which should be one of "tex", "asciimath" and "mml").
    * Inline elements get a preceding "MathJax_Preview" span element, for block elements this is created as a div and
    * a "mode=display" annotation is added to the script. */
  def renderMath(code: String, c: HtmlNodeRendererContext, pc: HtmlPageContext, mathType: String, block: Boolean): Unit = {
    pc.features.request(HtmlFeatures.MathJax)
    pc.features.request(HtmlFeatures.JavaScript)
    val wr = c.getWriter
    if(block && mathType == "asciimath") // Work around a MathJax bug: AsciiMath is always displayed inline
      wr.tag("div", Map("class" -> "MJXc-display", "style" -> "text-align: center").asJava)
    wr.tag(if(block) "div" else "span", Map("class" -> "MathJax_Preview").asJava)
    wr.text("[math]")
    wr.tag(if(block) "/div" else "/span")
    wr.tag("script", Map("type" -> ("math/" + mathType + (if(block) "; mode=display" else ""))).asJava)
    if(mathType == "math/mml") wr.raw(code)
    else wr.raw(Util.encodeScriptContent(code))
    wr.tag("/script")
    if(block && mathType == "asciimath") wr.tag("/div")
  }

  def renderEmoji(pc: HtmlPageContext)(n: Emoji, c: HtmlNodeRendererContext): Unit = {
    val wr = c.getWriter
    if(n.uri ne null) {
      wr.raw(s"""<img class="emoji" title="${n.name}" alt="""")
      wr.text(n.unicode)
      wr.raw("""" src="""")
      wr.text(pc.slp.resolveLink(pc.page.uri, n.uri.toString, "image", false))
      wr.raw(""""/>""")
    } else {
      wr.raw(s"""<span class="emoji" title="${n.name}">""")
      wr.text(n.unicode)
      wr.raw("</span>")
    }
  }

  def renderers(pc: HtmlPageContext): Seq[HtmlNodeRendererFactory] = Seq(
    SimpleHtmlNodeRenderer(renderEmoji(pc) _),
    SimpleHtmlNodeRenderer(renderAttributedBlockQuote _),
    SimpleHtmlNodeRenderer(renderAttributedHeading(pc) _),
    SimpleHtmlNodeRenderer(renderTabView(pc) _),
    SimpleHtmlNodeRenderer(renderInlineMath(pc) _),
    SimpleHtmlNodeRenderer(renderMathBlock(pc) _),
    SimpleHtmlNodeRenderer(renderFencedCodeBlock(pc) _),
    SimpleHtmlNodeRenderer(renderIndentedCodeBlock(pc) _),
    SimpleHtmlNodeRenderer(renderInlineCode(pc) _),
    SimpleHtmlNodeRenderer(renderHighlitBlock(pc) _),
    SimpleHtmlNodeRenderer(renderHighlitInline(pc) _),
    SimpleHtmlNodeRenderer(renderSpecialImageInline(pc) _),
    SimpleHtmlNodeRenderer(renderSpecialImageBlock(pc) _),
    SimpleHtmlNodeRenderer(renderIndexBlock(pc) _)
  )

  /** If MathJax is needed by the page, add all resources and return the resolved main script URI and inline config. */
  def addMathJaxResources(pc: HtmlPageContext): Option[(URI, Option[ConfigObject])] = if(pc.features.handle(HtmlFeatures.MathJax)) {
    val loadConfig = tc.getStringListOr("global.mathJax.loadConfig").mkString(",")
    val inlineConfig = tc.getConfigOpt("global.mathJax.inlineConfig").map(_.root())
    for(path <- WebJarSupport.listAssets("mathjax", "/"))
      if(!mathJaxExclude.matchesPath(path))
        pc.res.get(s"webjar:/mathjax/$path", "mathjax/", minified=true)
    val u = pc.res.get(s"webjar:/mathjax/MathJax.js", "mathjax/", minified=true)
    val u2 = if(loadConfig.isEmpty) u else u.copy(query = "config="+loadConfig)
    Some(u2, inlineConfig)
  } else None

  def createSiteContext(site: Site): HtmlSiteContext =
    new HtmlSiteContext(this, site)

  def createPageContext(siteContext: HtmlSiteContext, page: Page): HtmlPageContext =
    new HtmlPageContext(siteContext, page)

  def createPageModel(pc: HtmlPageContext, renderer: HtmlRenderer): HtmlPageModel =
    new HtmlPageModel(pc, renderer)

  def createSiteModel(pms: Vector[HtmlPageModel]): HtmlSiteModel = new HtmlSiteModel(this, pms)

  def render(site: Site): Unit = {
    val siteContext = createSiteContext(site)

    val pageModels = logTime("Page rendering took") {
      global.parMap(site.pages) { p =>
        val file = targetFile(p.uriWithSuffix(suffix))
        try {
          val templateName = global.userConfig.theme.getConfig(p.config).getString("template")
          logger.debug(s"Rendering page ${p.uri} to file $file with template ${templateName}")
          val pc = createPageContext(siteContext, p)
          pc.slp(p)
          val renderer = renderers(pc).foldLeft(HtmlRenderer.builder()) { case (z, n) => z.nodeRendererFactory(n) }
            .extensions(p.extensions.htmlRenderer.asJava).build()
          val pm = createPageModel(pc, renderer)
          val formatted = getTemplate(templateName).render(pm).body.trim
          file.parent.createDirectories()
          val minifyInlineJS = minifyJS && !pc.features.isRequested(HtmlFeatures.MathJax) // HtmlCompressor cannot handle non-JavaScript <script> tags
          val min =
            if(minifyHTML) Util.htmlCompressorMinimize(formatted, minimizeCss = minifyCSS, minimizeJs = minifyInlineJS) else formatted+'\n'
          file.write(min)(charset = StandardCharsets.UTF_8)
          Some(pm)
        } catch { case ex: Exception =>
          logger.error(s"Error rendering page ${p.uri} to $file", ex)
          None
        }
      }.flatten
    }

    try createSearchIndex(site)
    catch { case ex: Exception => logger.error(s"Error creating search index", ex) }

    siteContext.staticResources.foreach { case (sourceFile, uri) =>
      val file = targetFile(uri)
      logger.debug(s"Copying static resource $uri to file $file")
      try {
        file.parent.createDirectories()
        sourceFile.copyTo(file, overwrite = true)
      } catch { case ex: Exception =>
        logger.error(s"Error copying static resource file $sourceFile to $file", ex)
      }
    }

    val siteModel = createSiteModel(pageModels)
    implicit val utf8Codec = Codec.UTF8
    siteModel.siteResources.valuesIterator.filter(_.sourceURI.getScheme != "site").foreach { rs =>
      val file = targetFile(rs.targetURI)
      logger.debug(s"Copying theme resource ${rs.resolvedSourceURI} to file $file")
      try {
        if(rs.sourceURI.getScheme == "template") {
          val t = getResourceTemplate(rs.sourceURI.getPath.replaceAll("^/*", "").replace('.', '_'))
          file.parent.createDirectories()
          file.write(t.render(siteModel).body)
        } else {
          rs.minifiableType match {
            case Some("css") if minifyCSS => Util.copyToFileWithTextTransform(rs.resolvedSourceURI.toURL, file) { s =>
              try CSSO.minify(s) catch { case ex: Exception =>
                logger.error(s"Error minifying theme CSS file ${rs.sourceURI} with CSSO", ex)
                s
              }
            }
            case Some("js") if minifyJS => Util.copyToFileWithTextTransform(rs.resolvedSourceURI.toURL, file) { s =>
              try Util.closureMinimize(s, rs.sourceURI.toString) catch { case ex: Exception =>
                logger.error(s"Error minifying theme JS file ${rs.sourceURI} with Closure Compiler", ex)
                s
              }
            }
            case _ => Util.copyToFile(rs.resolvedSourceURI.toURL, file)
          }
        }
      } catch { case ex: Exception =>
        logger.error(s"Error copying theme resource ${rs.resolvedSourceURI} to $file", ex)
      }
    }

    val unhandled = siteModel.features.unhandled
    if(!unhandled.isEmpty)
      logger.warn("The following features are required by the site but not provided by the theme: "+unhandled.mkString(", "))
  }

  protected def createSearchIndex(site: Site): Unit = {
    if(tc.hasPath("global.pages.search") && tc.hasPath("global.searchIndex")) {
      val searchPage = Util.siteRootURI.resolve(tc.getString("global.pages.search"))
      val searchIndexFile = targetFile(Util.siteRootURI.resolve(tc.getString("global.searchIndex")))
      logger.debug("Writing search index to "+searchIndexFile)
      val exclude = new FileMatcher(tc.getStringListOr("global.searchExcludePages").toVector)
      val excerptLength = tc.getIntOr("global.searchExcerptLength")
      val idx = ElasticlunrSearch.createIndex
      site.pages.foreach { p =>
        if(exclude.matchesPath(p.uri.getPath))
          logger.debug(s"Excluding page ${p.uri} from search index")
        else {
          val body = NodeUtil.extractText(p.doc).trim
          val title = site.findTocEntry(p).flatMap(_.title).orElse(p.section.title).getOrElse("").trim
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
          val keywords = p.config.getStringOr("meta.keywords", "")
          idx.add(title, body, excerpt, keywords, link)
        }
      }
      implicit val codec = Codec.UTF8
      searchIndexFile.write("window._searchIndex = "+idx.toJSON+";")
    }
  }

  type Template = Template1[HtmlPageModel, HtmlFormat.Appendable]
  private[this] val templateBase = getClass.getName.replaceAll("\\.[^\\.]*$", "") + ".html"
  private[this] val templates = new mutable.HashMap[String, Template]
  def getTemplate(name: String) = templates.getOrElseUpdate(name, {
    val className = s"$templateBase.$name"
    logger.debug("Creating template from class "+className)
    Class.forName(className).newInstance().asInstanceOf[Template]
  })

  type ResourceTemplate = Template1[HtmlSiteModel, TxtFormat.Appendable]
  private[this] val resourceTemplateBase = getClass.getName.replaceAll("\\.[^\\.]*$", "") + ".txt"
  private[this] val resourceTemplates = new mutable.HashMap[String, ResourceTemplate]
  def getResourceTemplate(name: String) = resourceTemplates.getOrElseUpdate(name, {
    val className = s"$resourceTemplateBase.$name"
    logger.debug("Creating resource template from class "+className)
    Class.forName(className).newInstance().asInstanceOf[ResourceTemplate]
  })
}

class HtmlFeatures(parents: Iterable[HtmlFeatures]) {
  import HtmlFeatures._

  val requestedFeatures: mutable.Set[Feature] = mutable.Set[Feature]() ++= parents.flatMap(_.requestedFeatures)
  val handledFeatures: mutable.Set[Feature] = mutable.Set[Feature]() ++= parents.flatMap(_.handledFeatures)

  def request(f: Feature): Unit = requestedFeatures += f
  def isRequested(f: Feature): Boolean = requestedFeatures.contains(f)
  def handle(f: Feature): Boolean =
    if(requestedFeatures.contains(f)) { handledFeatures += f; true } else false
  def isHandled(f: Feature): Boolean = handledFeatures.contains(f)

  def unhandled: scala.collection.Set[Feature] = requestedFeatures -- handledFeatures
}

object HtmlFeatures {
  /** A feature that is required on the client side or as part of the generated site. It can be requested during
    * content rendering. The theme needs to handle all requested features and add the appropriate support to the
    * site, otherwise a warning is logged. */
  trait Feature
  case object JavaScript extends Feature
  case object MathJax extends Feature
  case object Mermaid extends Feature
}

/** The site context is created before page preprocessing */
class HtmlSiteContext(val theme: HtmlTheme, val site: Site) {
  val staticResources = theme.global.findStaticResources
  val staticResourceURIs = staticResources.iterator.map(_._2.getPath).toSet
  val slpLookup =
    new SpecialLinkProcessor.Lookup(site, if(theme.global.userConfig.allowTargetLinks) Some(theme.suffix) else None)
}

/** The page context is available for preprocessing a page */
class HtmlPageContext(val siteContext: HtmlSiteContext, val page: Page) {
  private[this] var last = -1
  def newID(): String = {
    last += 1
    s"_id$last"
  }

  private lazy val pageTC = siteContext.theme.global.userConfig.theme.getConfig(page.config)
  def pageConfig(path: String): Option[String] = page.config.getStringOpt(path)
  def themeConfig(path: String): Option[String] = pageTC.getStringOpt(path)
  def themeConfigInt(path: String): Option[Int] = pageTC.getIntOpt(path)
  def themeConfigStringList(path: String): Option[Seq[String]] = pageTC.getStringListOpt(path)
  def themeConfigValueList(path: String): Option[Seq[ConfigValue]] = pageTC.getListOpt(path)
  def themeConfigBoolean(path: String): Option[Boolean] = pageTC.getBooleanOpt(path)
  lazy val siteNav: Option[Vector[ExpandTocProcessor.TocItem]] = themeConfig("siteNav") match {
    case Some(uri) =>
      val tocBlock = SpecialImageProcessor.parseTocURI(uri, siteContext.theme.global.userConfig)
      Some(ExpandTocProcessor.buildTocTree(tocBlock, siteContext.site.toc, page))
    case None => None
  }

  def searchLink: Option[String] = siteContext.theme.tc.getStringOpt("global.pages.search").map { uri =>
    Util.rewriteIndexPageLink(Util.relativeSiteURI(page.uri, Util.siteRootURI.resolve(uri).replaceSuffix(".md", ".html")), siteContext.theme.indexPage).toString
  }
  def searchIndex: Option[String] = siteContext.theme.tc.getStringOpt("global.searchIndex").map { uri =>
    Util.relativeSiteURI(page.uri, Util.siteRootURI.resolve(uri)).toString
  }
  def resolveLink(dest: String): String = slp.resolveLink(page.uri, dest, "link", true)

  def sections: Vector[Section] = page.section.children

  lazy val tocLocation: Option[TocLocation] = siteContext.site.findTocLocation(page)

  def stringNode(name: String): Option[Node] = themeConfig(s"strings.$name").map { md =>
    val snippet = page.parseAndProcessSnippet(md)
    slp(snippet)
    snippet.doc
  }

  val res = new PageResources(page, siteContext.theme, {
    val dir = siteContext.theme.tc.getString(s"global.resourceDir")
    Util.siteRootURI.resolve(if(dir.endsWith("/")) dir else dir + "/")
  })

  val slp = new SpecialLinkProcessor(res, siteContext.slpLookup, siteContext.theme.suffix, siteContext.theme.indexPage, siteContext.staticResourceURIs)

  val features = new HtmlFeatures(Seq.empty)
}

/** The page model is available for rendering a preprocessed page with a template. Creating the HtmlPageModel
  * forces the content to be rendered. */
class HtmlPageModel(val pc: HtmlPageContext, renderer: HtmlRenderer) {
  val title = HtmlFormat.escape(pc.page.section.title.getOrElse(""))
  val content = HtmlFormat.raw(renderer.render(pc.page.doc))

  private[this] lazy val mathJaxResources = pc.siteContext.theme.addMathJaxResources(pc)
  lazy val mathJaxMain: Option[URI] = mathJaxResources.map(_._1)
  lazy val mathJaxInline: Option[Html] = mathJaxResources.flatMap(_._2).flatMap { cv =>
    if(cv.isEmpty) None
    else Some(HtmlFormat.raw(cv.render(ConfigRenderOptions.concise())))
  }
  lazy val mathJaxSkipStartupTypeset =
    mathJaxResources.flatMap(_._2).map(_.toConfig.getBooleanOr("skipStartupTypeset")).getOrElse(false)

  def stringHtml(name: String): Option[Html] = pc.stringNode(name).map(n => HtmlFormat.raw(renderer.render(n)))
  def stringText(name: String): Option[Html] = pc.stringNode(name).map(n => HtmlFormat.escape(NodeUtil.extractText(n)))

  def stringHtmlInline(name: String): Option[Html] = pc.stringNode(name).map { n =>
    val sb = new java.lang.StringBuilder
    def render(n: Node): Unit = {
      if(n.isInstanceOf[Block]) n.children.foreach(render)
      else {
        if(sb.length() > 0) sb.append(' ')
        renderer.render(n, sb)
      }
    }
    render(n)
    HtmlFormat.raw(sb.toString)
  }

  class NavLink(val target: Option[URI], val title: Option[Html], val rel: Option[String], textName: Option[String]) {
    lazy val text: Option[Html] = textName.flatMap(stringHtmlInline)

    /** Check for non-empty text without forcing rendering */
    def hasText: Boolean =
      textName.flatMap(s => pc.themeConfig(s"strings.$s")).map(_.nonEmpty).getOrElse(false)

    def this(to: Option[TocEntry], rel: Option[String], textName: Option[String]) = this(
      to.map(te => new URI(pc.resolveLink(te.page.uri.toString))),
      to.flatMap(te => te.title.map(HtmlFormat.escape)),
      rel, textName
    )
  }

  protected def editNavLink: Option[NavLink] = {
    if(pc.page.sourceFileURI.isEmpty) None
    else pc.themeConfig("editPage").map { s =>
      val target = s.replace("[page]", pc.page.uri.getPath.dropWhile(_ == '/'))
      new NavLink(Some(new URI(target)), None, Some("edit"), Some("navEdit"))
    }
  }

  protected def inTocNavLinks: Map[String, NavLink] = if(pc.tocLocation.isDefined) Map(
    "first" -> new NavLink(pc.siteContext.site.toc.headOption, Some("start"), Some("navFirst")),
    "prev" -> new NavLink(pc.tocLocation.flatMap(_.previous), Some("prev"), Some("navPrev")),
    "next" -> new NavLink(pc.tocLocation.flatMap(_.next), Some("next"), Some("navNext")),
    "last" -> new NavLink(pc.siteContext.site.toc.headOption, None, Some("navLast"))
  ) else Map.empty

  protected def tocNavLink: Option[NavLink] =
    pc.siteContext.site.pages.find(_.syntheticName == Some("toc")).flatMap(p =>
      if(p == pc.page) None
      else Some(new NavLink(Some(new TocEntry(p, p.section.title)), Some("toc"), Some("navToc")))
    )

  protected def indexNavLink: Option[NavLink] =
    pc.siteContext.site.pages.find(_.syntheticName == Some("index")).flatMap(p =>
      if(p == pc.page) None
      else Some(new NavLink(Some(new TocEntry(p, p.section.title)), Some("index"), Some("navIndex")))
    )

  lazy val navLinks: Map[String, NavLink] =
    inTocNavLinks ++ tocNavLink.map(n => "toc" -> n) ++ indexNavLink.map(n => "index" -> n) ++ editNavLink.map(n => "edit" -> n)

  lazy val activeRelNavLinks: Seq[NavLink] = navLinks.values.iterator.filter(n => n.rel.isDefined && n.target.isDefined).toSeq

  lazy val customLinks: Seq[Html] = pc.themeConfigValueList("links").getOrElse(Seq.empty).flatMap { cv =>
    try {
      val co = cv.asInstanceOf[ConfigObject]
      val attrs = co.entrySet().iterator().asScala.flatMap { me =>
        val v = me.getValue.unwrapped()
        if(v eq null) None else Some((me.getKey, v.toString))
      }.map {
        case ("href", href) => ("href", pc.resolveLink(href))
        case other => other
      }.toMap
      if(attrs.isEmpty) None
      else {
        val attrsText = attrs.iterator.map { case (k, v) =>
          HtmlFormat.escape(k).toString + "=\"" + HtmlFormat.escape(v).toString + "\""
        }.mkString(" ")
        Some(HtmlFormat.raw(s"<link $attrsText />"))
      }
    } catch {
      case ex: Exception =>
        pc.siteContext.theme.logger.error("Error processing \"links\" entry: "+cv, ex)
        None
    }
  }
}

class HtmlSiteModel(val theme: HtmlTheme, pms: Vector[HtmlPageModel]) {
  val features = new HtmlFeatures(pms.map(_.pc.features))
  val siteResources = pms.flatMap(_.pc.res.mappings.map(r => (r.resolvedSourceURI, r))).toMap

  def themeConfig(path: String): Option[String] = theme.tc.getStringOpt(path)
  def themeConfigBoolean(path: String): Option[Boolean] = theme.tc.getBooleanOpt(path)
}
