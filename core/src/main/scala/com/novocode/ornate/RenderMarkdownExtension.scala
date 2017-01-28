package com.novocode.ornate

import com.novocode.ornate.commonmark.AttributedFencedCodeBlock
import com.novocode.ornate.config.ConfiguredObject
import org.commonmark.node.{AbstractVisitor, FencedCodeBlock}
import com.novocode.ornate.commonmark.NodeExtensionMethods.nodeToNodeExtensionMethods

/** Expand Markdown source from a fenced code block into the document. */
class RenderMarkdownExtension(co: ConfiguredObject) extends Extension {

  val pp: PageProcessor = new PageProcessor {
    def runAt: Phase = Phase.Expand
    def apply(p: Page): Unit = p.doc.accept(new RenderMarkdownVisitor(p))
  }

  override def pageProcessors(site: Site) = Seq(pp)

  class RenderMarkdownVisitor(p: Page) extends AbstractVisitor {
    override def visit(n: FencedCodeBlock): Unit = {
      val attr = n.asInstanceOf[AttributedFencedCodeBlock]
      if(attr.getLanguage == Some("markdown") && attr.simpleAttrs.contains("render")) {
        val snippet = p.parseAndProcessSnippet(attr.getLiteral, stopBefore = pp).doc
        snippet.accept(this)
        snippet.children.foreach(n.insertBefore _)
        n.unlink()
      }
    }
  }
}
