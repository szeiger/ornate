package com.novocode.mdoc.theme

import java.net.{URL, URI}
import java.util.Collections

import better.files.File.OpenOptions
import com.novocode.mdoc._
import com.novocode.mdoc.commonmark.NodeExtensionMethods._

import better.files._
import com.novocode.mdoc.commonmark._
import com.novocode.mdoc.config.Global
import com.novocode.mdoc.highlight.HighlightTarget
import org.commonmark.html.HtmlRenderer
import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.html.renderer.{NodeRendererContext, NodeRenderer}
import org.commonmark.node._
import play.twirl.api.{Html, Template1, HtmlFormat}

import scala.StringBuilder
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Codec

/** Base class for Twirl-based HTML themes */
class HtmlTheme(global: Global) extends Theme(global) { self =>
  import HtmlTheme._
  val suffix = ".html"

  val attributedHeadingRenderer = SimpleHtmlNodeRenderer { (n: AttributedHeading, c: NodeRendererContext) =>
    val html = c.getHtmlWriter
    val htag = s"h${n.getLevel}"
    html.line
    val attrs = c.extendAttributes(n, Collections.emptyMap[String, String])
    if(n.id ne null) attrs.put("id", n.id)
    html.tag(htag, attrs)
    n.children.toVector.foreach(c.render)
    html.tag('/' + htag)
    html.line
  }

  def targetFile(uri: URI, base: File): File =
    uri.getPath.split('/').filter(_.nonEmpty).foldLeft(base) { case (f, s) => f / s }

  def renderCode(n: Node, raw: String, lang: Option[String], c: NodeRendererContext, block: Boolean): Unit = {
    val attrs = new mutable.HashMap[String, String]
    lang match {
      case Some(s) => attrs += "class" -> s"language-${s}"
      case None =>
    }
    val html = c.getHtmlWriter
    if(block) {
      html.line
      html.tag("pre")
    }
    html.tag("code", c.extendAttributes(n, attrs.asJava))
    html.raw(raw)
    html.tag("/code")
    if(block) {
      html.tag("/pre")
      html.line
    }
  }

  def fencedCodeBlockRenderer(page: Page, css: ThemeResources) = SimpleHtmlNodeRenderer { (n: FencedCodeBlock, c: NodeRendererContext) =>
    val info = if(n.getInfo eq null) Vector.empty else n.getInfo.split(' ').filter(_.nonEmpty).toVector
    val lang = info.headOption
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, lang, HighlightTarget.FencedCodeBlock, page)
    hlr.css.foreach(u => css.getURI(u, null, u.getPath.endsWith(".css")))
    renderCode(n, hlr.html.toString, lang.orElse(hlr.language), c, true)
  }

  def indentedCodeBlockRenderer(page: Page, css: ThemeResources) = SimpleHtmlNodeRenderer { (n: IndentedCodeBlock, c: NodeRendererContext) =>
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, None, HighlightTarget.IndentedCodeBlock, page)
    hlr.css.foreach(u => css.getURI(u, null, u.getPath.endsWith(".css")))
    renderCode(n, hlr.html.toString, hlr.language, c, true)
  }

  def inlineCodeRenderer(page: Page, css: ThemeResources) = SimpleHtmlNodeRenderer { (n: Code, c: NodeRendererContext) =>
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, None, HighlightTarget.InlineCode, page)
    hlr.css.foreach(u => css.getURI(u, null, u.getPath.endsWith(".css")))
    renderCode(n, hlr.html.toString, hlr.language, c, false)
  }

  class ThemeResources(val page: Page, tpe: String) extends Resources {
    private[this] val baseURI = {
      val dir = global.userConfig.theme.config.getString(s"global.dirs.$tpe")
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
              val tname = (if(targetFile eq null) suggestRelativePath(sourceURI) else targetFile).replaceAll("^/*", "")
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

  class PageModelImpl(p: Page, val renderer: HtmlRenderer, val css: ThemeResources) extends PageModel {
    def theme = self
    val title = HtmlFormat.escape(p.section.title.getOrElse(""))
    val content = HtmlFormat.raw(renderer.render(p.doc))
    val js = new ThemeResources(p, "js")
    val image = new ThemeResources(p, "image")
  }

  def render(site: Site): Unit = {
    val staticResources = global.findStaticResources
    val slp = new SpecialLinkProcessor(site, suffix, staticResources.iterator.map(_._2.getPath).toSet)
    val siteResources = new mutable.HashMap[URL, ResourceSpec]

    site.pages.foreach { p =>
      val file = targetFile(p.uriWithSuffix(suffix), global.userConfig.targetDir)
      try {
        val templateName = p.config.getString("template")
        logger.debug(s"Rendering page ${p.uri} to file $file with template ${templateName}")
        slp(p)
        val css = new ThemeResources(p, "css")
        val template = getTemplate(templateName)
        val renderer = HtmlRenderer.builder()
          .nodeRendererFactory(attributedHeadingRenderer)
          .nodeRendererFactory(fencedCodeBlockRenderer(p, css))
          .nodeRendererFactory(indentedCodeBlockRenderer(p, css))
          .nodeRendererFactory(inlineCodeRenderer(p, css))
          .extensions(p.extensions.htmlRenderer.asJava).build()
        val pm = new PageModelImpl(p, renderer, css)
        val formatted = template.render(pm).body.trim
        siteResources ++= (pm.css.mappings ++ pm.js.mappings ++ pm.image.mappings).map(r => (r.url, r))
        file.parent.createDirectories()
        file.write(formatted+'\n')(codec = Codec.UTF8)
      } catch { case ex: Exception =>
        logger.error(s"Error rendering page ${p.uri} to $file", ex)
      }
    }

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
  }

  trait Resources {
    protected def mappings: Iterable[ResourceSpec]
    protected def page: Page
    protected def getURI(uri: URI, targetFile: String, keepLink: Boolean): URI

    final def get(path: String, targetFile: String = null, keepLink: Boolean = false): URI =
      Util.relativeSiteURI(page.uri, getURI(new URI("theme:/").resolve(path), targetFile, keepLink))
    final def require(path: String, targetFile: String = null, keepLink: Boolean = true): Unit =
      get(path, targetFile, keepLink)
    final def links: Iterable[URI] =
      mappings.collect { case r: ResourceSpec if r.keepLink => Util.relativeSiteURI(page.uri, r.uri) }
  }

  case class ResourceSpec(sourceURI: URI, url: URL, uri: URI, keepLink: Boolean)
}
