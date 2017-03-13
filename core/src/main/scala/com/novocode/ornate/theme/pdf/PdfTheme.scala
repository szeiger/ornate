package com.novocode.ornate.theme.pdf

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import java.util.Collections

import better.files.File
import com.novocode.ornate._
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.commonmark._
import com.novocode.ornate.config.Global
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.novocode.ornate.highlight.HighlightResult
import com.novocode.ornate.theme._
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.{Block, Node}
import org.commonmark.renderer.html.{HtmlNodeRendererContext, HtmlNodeRendererFactory, HtmlRenderer}

import scala.collection.JavaConverters._

class PdfTheme(global: Global) extends HtmlTheme(global) {
  def pdfTargetDir: File = super.targetDir
  override def targetDir: File = super.targetDir / tc.getString("global.tempDir")

  override def render(site: Site): Unit = {
    if(site.toc.isEmpty)
      logger.error("Non-empty TOC required for PDF rendering")
    else if(targetDir.parent != pdfTargetDir) // Make sure that we won't do anything stupid like deleting "/"
      logger.error(s"Temporary dir $targetDir must be in target dir $pdfTargetDir")
    else {
      if(targetDir.exists) targetDir.delete()
      super.render(site) // TODO: Don't render pages that are not in the TOC
      val sourceFiles = site.toc.map(te => targetFile(te.page.uriWithSuffix(suffix)))
      val outFile = pdfTargetDir / tc.getString("global.targetFile")
      val cmd =
        tc.getString("global.wkhtmltopdf") +:
        tc.getStringListOr("global.extraArgs") ++:
        sourceFiles.map(f => pdfTargetDir.path.relativize(f.path).toString) ++:
        Vector(tc.getString("global.targetFile"))
      logger.info("Generating PDF file "+outFile)
      logger.debug("Launching process: "+cmd.mkString(" "))
      val pb = new ProcessBuilder(cmd.toArray: _*)
      pb.directory(pdfTargetDir.toJava)
      pb.redirectErrorStream(true)
      val p = pb.start()
      p.getOutputStream.close()
      val in = new BufferedReader(new InputStreamReader(p.getInputStream))
      for(line <- in.lines.iterator().asScala)
        logger.info("wkhtmltopdf: "+line)
      val result = p.waitFor()
      if(result != 0) logger.error("wkhtmltopdf terminated with error code "+result)
    }
  }

  override def renderAttributedHeading(pc: HtmlPageContext)(n: AttributedHeading, c: HtmlNodeRendererContext): Unit = if(n.id ne null) {
    val wr = c.getWriter
    val htag = s"h${n.getLevel}"
    wr.line
    val attrs = Map[String, String]("id" -> n.id)
    wr.tag(htag, c.extendAttributes(n, attrs.asJava))
    n.children.toVector.foreach(c.render)
    wr.tag('/' + htag)
    wr.line
  } else super.renderAttributedHeading(pc)(n, c)

  // Disabled for now -- Mermaid support is broken with wkhtmltopdf 0.12
  override def renderMermaid(n: AttributedFencedCodeBlock, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = {
    pc.features.request(HtmlFeatures.Mermaid)
  }

  override def renderTabView(pc: HtmlPageContext)(n: TabView, c: HtmlNodeRendererContext): Unit = {
    val wr = c.getWriter
    n.children.foreach {
      case item: TabItem =>
        wr.tag("p")
        wr.text(item.title + ":")
        wr.tag("/p")
        item.children.toVector.foreach(c.render)
      case n => c.render(n)
    }
  }

  override def renderCode(hlr: HighlightResult, code: Node, c: HtmlNodeRendererContext, pc: HtmlPageContext): Unit = if(code.isInstanceOf[Block]) {
    val wr = c.getWriter
    wr.raw("""<div class="a_code_block">""")
    super.renderCode(hlr, code, c, pc)
    wr.raw("""</div>""")
  } else super.renderCode(hlr, code, c, pc)

  def renderTableBlock(n: TableBlock, c: HtmlNodeRendererContext): Unit = {
    val wr = c.getWriter
    wr.line
    wr.raw("""<div class="a_table">""")
    wr.tag("table", c.extendAttributes(n, Collections.emptyMap[String, String]))
    n.children.toVector.foreach(c.render)
    wr.tag("/table")
    wr.raw("</div>")
    wr.line()
  }

  override def renderers(pc: HtmlPageContext): Seq[HtmlNodeRendererFactory] =
    SimpleHtmlNodeRenderer(renderTableBlock _) +: super.renderers(pc)

  override def createPageModel(pc: HtmlPageContext, renderer: HtmlRenderer) = new PdfPageModel(pc, renderer)

  override def createSiteModel(pms: Vector[HtmlPageModel]): HtmlSiteModel = new HtmlSiteModel(this, pms) {
    features.handle(HtmlFeatures.JavaScript)
  }
}

class PdfPageModel(pc: HtmlPageContext, renderer: HtmlRenderer) extends HtmlPageModel(pc, renderer) {
  def doneTriggerCount: Int = 1 +
    (if(pc.features.isHandled(HtmlFeatures.MathJax)) 1 else 0)
    /* + (if(pc.features.isHandled(HtmlFeatures.Mermaid)) 1 else 0)*/
}
