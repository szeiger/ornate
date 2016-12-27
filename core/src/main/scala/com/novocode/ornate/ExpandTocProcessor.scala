package com.novocode.ornate

import com.novocode.ornate.commonmark.{AttributedHeading, TocBlock}
import com.novocode.ornate.commonmark.NodeExtensionMethods.nodeToNodeExtensionMethods
import org.commonmark.node._

import scala.collection.mutable.ArrayBuffer

class ExpandTocProcessor(toc: Vector[TocEntry]) extends PageProcessor with Logging {
  import ExpandTocProcessor._

  def runAt: Phase = Phase.Special

  def log(prefix: String, ti: TocItem): Unit = {
    logger.debug(s"""$prefix- "${ti.text.getOrElse("")}" -> ${ti.target.getOrElse("")}""")
    ti.children.foreach(ch => log(prefix+"  ", ch))
  }

  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: CustomBlock): Unit = n match {
        case n: TocBlock =>
          val items = buildTocTree(n, toc, p)
          if(items.nonEmpty) {
            val ul = new BulletList
            ul.setTight(true)
            items.foreach { i =>
              log("", i)
              ul.appendChild(i.toNode)
            }
            n.replaceWith(ul)
          } else n.unlink()
        case _ =>
          super.visit(n)
      }
    })
  }
}

object ExpandTocProcessor {
  case class TocItem(text: Option[String], target: Option[String], children: Vector[TocItem], focused: Boolean) {
    def toNode: ListItem = {
      val li = new ListItem
      text.foreach { t =>
        val p = new Paragraph
        li.appendChild(p)
        val text = new Text(t)
        target match {
          case Some(dest) =>
            val link = new Link(dest, null)
            p.appendChild(link)
            link.appendChild(text)
          case None =>
            p.appendChild(text)
        }
      }
      if(children.nonEmpty) {
        val ul = new BulletList
        ul.setTight(true)
        li.appendChild(ul)
        children.foreach(ch => ul.appendChild(ch.toNode))
      }
      li
    }
  }

  /** Turn a `Section` recursively into a `TocItem` tree */
  private def sectionToc(s: Section, p: Page, pTitle: Option[String], maxLevel: Int, focused: Boolean): Option[TocItem] = {
    if(s.level > maxLevel) None else {
      val (title, target) = s match {
        case PageSection(title, children) => (pTitle, Some(p.uri.toString))
        case UntitledSection(_, children) => (None, None)
        case s @ HeadingSection(id, _, title, children) =>
          val id = s.heading match {
            case h: AttributedHeading => Option(h.id)
            case _ => None
          }
          (Some(title), id.map(p.uri.toString + "#" + _))
      }
      val ch = s.children.flatMap(ch => sectionToc(ch, p, pTitle, maxLevel, focused))
      if(title.isEmpty && target.isEmpty && ch.isEmpty) None
      else Some(TocItem(title, target, ch, focused))
    }
  }

  /** Remove items without a title and move their children into the preceding item */
  private def mergeHierarchies(items: Vector[TocItem]): Vector[TocItem] = {
    val b = new ArrayBuffer[TocItem](items.length)
    items.foreach { item =>
      if(b.isEmpty || item.text.nonEmpty || item.target.nonEmpty) b += item
      else {
        val prev = b.remove(b.length-1)
        b += prev.copy(children = mergeHierarchies(prev.children ++ item.children))
      }
    }
    b.toVector
  }

  /** Merge top-level `TocItem` for the page itself with the first element of the page */
  private def mergePages(items: Vector[TocItem]): Vector[TocItem] = mergeHierarchies(items.flatMap { pageItem =>
    def rewriteFirstIn(items: Vector[TocItem]): Vector[TocItem] = {
      if(items.isEmpty) items // should never happen
      else {
        val h = items.head
        if(h.text.nonEmpty || h.target.nonEmpty || h.children.isEmpty)
          h.copy(text = pageItem.text.orElse(h.text), target = pageItem.target) +: items.tail
        else h.copy(children = rewriteFirstIn(h.children)) +: items.tail
      }
    }
    rewriteFirstIn(pageItem.children)
  })

  def buildTocTree(n: TocBlock, toc: Vector[TocEntry], page: Page): Vector[TocItem] = {
    if(n.local) {
      val title = toc.find(_.page eq page) match {
        case Some(e) => e.title
        case None => page.section.title
      }
      sectionToc(page.section, page, title, n.focusMaxLevel, true).toVector.flatMap(_.children)
    } else {
      val items = toc.flatMap(e => sectionToc(e.page.section, e.page, e.title, if(e.page eq page) n.focusMaxLevel else n.maxLevel, e.page eq page))
      if(n.mergeFirst) mergePages(items) else items
    }
  }
}
