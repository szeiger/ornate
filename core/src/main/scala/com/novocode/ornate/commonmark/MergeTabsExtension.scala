package com.novocode.ornate.commonmark

import NodeExtensionMethods._
import com.novocode.ornate._
import com.novocode.ornate.config.Global
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomBlock
import org.commonmark.node.FencedCodeBlock

/** Merge adjacent fenced code blocks into tabs. */
class MergeTabsExtension extends Extension {
  override def pageProcessors(site: Site) = Seq(new MergeTabsProcessor)
}

class MergeTabsProcessor extends PageProcessor with Logging {
  def runAt: Phase = Phase.Expand

  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: FencedCodeBlock): Unit = n.getParent match {
        case _: TabItem => // already merged
        case _ =>
          val all =
            (Iterator(n) ++ n.nextElements.collect[FencedCodeBlock] { case f: FencedCodeBlock => f }).map {
              case f: AttributedFencedCodeBlock => (f, f.defAttrs.get("tab"))
              case f => (f, Attributed.parse(n.getInfo).defAttrs.get("tab"))
            }.takeWhile(_._2.isDefined).toVector
          all.length match {
            case 0 =>
            case 1 =>
              logger.warn("Found fenced code block \""+all.head._2.get+"\" to merge but no adjacent mergeable blocks")
            case _ =>
              if(logger.isDebugEnabled)
                logger.debug("Merging fenced code blocks: "+all.map(_._2.get).mkString("\"", "\", \"", "\""))
              val tabs = new TabView
              n.replaceWith(tabs)
              all.foreach { case (f, t) =>
                val item = new TabItem(t.get)
                tabs.appendChild(item)
                item.appendChild(f)
              }
          }
      }
    })
  }
}

class TabView extends CustomBlock

class TabItem(var title: String) extends CustomBlock {
  override def toStringAttributes = s"title=$title"
}
