package com.novocode.ornate.commonmark

import com.novocode.ornate.{Extension, Page, Site}
import com.novocode.ornate.config.Global
import org.commonmark.node.{Heading, AbstractVisitor}

/** Give all headings an ID so they can be linked to from the TOC and other places.
  * Otherwise only explicitly attributed headings get an ID. */
class AutoIdentifiersExtension extends Extension {
  override def pageProcessors(global: Global, site: Site) = Seq(new AutoIdentifiersProcessor(site))
}

class AutoIdentifiersProcessor(site: Site) extends PageProcessor {
  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Heading): Unit = {
        n match {
          case n: AttributedHeading if n.id eq null =>
            p.section.allHeadings.find(_.heading eq n).foreach { s => n.id = s.id }
          case _ =>
        }
        super.visit(n)
      }
    })
  }
}
