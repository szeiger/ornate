package com.novocode.mdoc.theme

import java.net.URI
import java.util.Collections

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

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Codec

/** Base class for Twirl-based HTML themes */
class HtmlTheme(global: Global) extends Theme(global) with Logging { self =>
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

  def fencedCodeBlockRenderer(page: Page) = SimpleHtmlNodeRenderer { (n: FencedCodeBlock, c: NodeRendererContext) =>
    val info = if(n.getInfo eq null) Vector.empty else n.getInfo.split(' ').filter(_.nonEmpty).toVector
    val lang = info.headOption
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, lang, HighlightTarget.FencedCodeBlock, page)
    renderCode(n, hlr.html.toString, lang.orElse(hlr.language), c, true)
  }

  def indentedCodeBlockRenderer(page: Page) = SimpleHtmlNodeRenderer { (n: IndentedCodeBlock, c: NodeRendererContext) =>
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, None, HighlightTarget.IndentedCodeBlock, page)
    renderCode(n, hlr.html.toString, hlr.language, c, true)
  }

  def inlineCodeRenderer(page: Page) = SimpleHtmlNodeRenderer { (n: Code, c: NodeRendererContext) =>
    val hlr = global.highlighter.highlightTextAsHTML(n.getLiteral, None, HighlightTarget.InlineCode, page)
    renderCode(n, hlr.html.toString, hlr.language, c, false)
  }

  def render(site: Site): Unit = {
    val staticResources = global.findStaticResources
    val slp = new SpecialLinkProcessor(site, suffix, staticResources.iterator.map(_._2.getPath).toSet)

    site.pages.foreach { p =>
      slp(p)
      val file = targetFile(p.uriWithSuffix(suffix), global.userConfig.targetDir)
      val templateName = p.config.getString("template")
      logger.debug(s"Rendering page ${p.uri} to file $file with template ${templateName}")
      try {
        val template = getTemplate(templateName)
        val _renderer = HtmlRenderer.builder()
          .nodeRendererFactory(attributedHeadingRenderer)
          .nodeRendererFactory(fencedCodeBlockRenderer(p))
          .nodeRendererFactory(indentedCodeBlockRenderer(p))
          .nodeRendererFactory(inlineCodeRenderer(p))
          .extensions(p.extensions.htmlRenderer.asJava).build()
        val pm: PageModel = new PageModel {
          def renderer = _renderer
          def theme = self
          val title = HtmlFormat.escape(p.section.title.getOrElse(""))
          val content = HtmlFormat.raw(renderer.render(p.doc))
        }
        val formatted = template.render(pm).body.trim
        file.parent.createDirectories()
        file.write(formatted+'\n')(codec = Codec.UTF8)
      } catch { case ex: Exception =>
        logger.error(s"Error rendering page ${p.uri} to $file", ex)
      }
    }

    staticResources.map { case (sourceFile, uri) =>
      val file = targetFile(uri, global.userConfig.targetDir)
      logger.debug(s"Copying static resource $uri to file $file")
      try sourceFile.copyTo(file, overwrite = true)
      catch { case ex: Exception =>
        logger.error(s"Error copying static resource file $sourceFile to $file", ex)
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
  }
}
