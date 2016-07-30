package com.novocode.mdoc.theme

import com.novocode.mdoc._
import com.novocode.mdoc.commonmark.NodeExtensionMethods._

import better.files._
import com.novocode.mdoc.commonmark.{TocBlock, SimpleHtmlNodeRenderer}
import org.commonmark.html.HtmlRenderer
import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.html.renderer.{NodeRendererContext, NodeRenderer}
import org.commonmark.node.HtmlBlock
import org.slf4j.LoggerFactory
import play.twirl.api.{Html, Template1, HtmlFormat}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Codec

abstract class Theme(site: Site, global: Global) {
  def render: Unit
}

/** Base class for Twirl HTML themes */
class HtmlTheme(site: Site, global: Global) extends Theme(site: Site, global: Global) { self =>
  import HtmlTheme._
  val logger = LoggerFactory.getLogger(getClass)

  val tocRenderer = SimpleHtmlNodeRenderer { (n: TocBlock, c: NodeRendererContext) =>
    c.getHtmlWriter.text("[TOC HERE]")
  }

  def render: Unit = {
    site.pages.foreach { p =>
      val file = p.targetFile(global.targetDir, ".html")
      val templateName = p.config.getString("template")
      logger.debug(s"Rendering page ${p.uri} to file $file with template ${templateName}")
      val template = getTemplate(templateName)
      val rendererExtensions = p.extensions.collect { case e: HtmlRendererExtension => e }.asJava
      val _renderer = HtmlRenderer.builder().nodeRendererFactory(tocRenderer).extensions(rendererExtensions).build()
      val pm: PageModel = new PageModel {
        def renderer = _renderer
        def theme = self
        val title = HtmlFormat.escape(p.title.getOrElse(""))
        val content = HtmlFormat.raw(renderer.render(p.doc))
      }
      val formatted = template.render(pm).body.trim
      file.parent.createDirectories()
      file.write(formatted)(codec = Codec.UTF8)
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

/** A theme that prints the document structure to stdout */
class Dump(site: Site, global: Global) extends Theme(site: Site, global: Global) {
  def render: Unit = {
    site.pages.foreach { p =>
      println(p)
      p.doc.dumpDoc()
    }
  }
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
