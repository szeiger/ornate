package com.novocode.mdoc.commonmark

import java.net.URI

import com.novocode.mdoc._
import NodeExtensionMethods._
import com.novocode.mdoc.config.UserConfig

import org.commonmark.node._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

abstract class PageProcessor extends (Page => Unit) {
  val logger = LoggerFactory.getLogger(getClass)
}

class SpecialImageProcessor(config: UserConfig) extends PageProcessor {
  val SpecialObjectMatcher = new SpecialImageParagraphMatcher(Set("toctree"))

  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    override def visit(n: Paragraph): Unit = n match {
      case SpecialObjectMatcher(r) => r.protocol match {
        case "toctree" =>
          val t = new TocBlock(
            r.attributes.get("maxlevel").map(_.toInt).getOrElse(config.tocMaxLevel),
            r.title,
            r.attributes.get("mergefirst").map(_.toBoolean).getOrElse(config.tocMergeFirst),
            r.attributes.get("local").map(_.toBoolean).getOrElse(false))
          n.replaceWith(t)
          r.image.children.foreach(t.appendChild)
        case _ =>
      }
      case n => super.visit(n)
    }
    override def visit(n: Image): Unit = {
      n match {
        case SpecialObjectMatcher.ImageMatcher(r) =>
          logger.error(s"Page ${p.uri}: Illegal inline special object ${r.image.getDestination} -- only block-level allowed")
        case n =>
      }
      super.visit(n)
    }
  })
}

class SpecialLinkProcessor(site: Site, suffix: String) extends PageProcessor {
  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Link): Unit = {
        if(n.getDestination.startsWith("abs:")) {
          val dest = n.getDestination.substring(4)
          logger.debug(s"Page ${p.uri}: Rewriting link ${n.getDestination} to $dest")
          n.setDestination(dest)
        } else {
          val uri = p.uri.resolve(n.getDestination)
          if(uri.getScheme == Util.siteRootURI.getScheme) {
            site.getPageFor(uri) match {
              case Some(t) =>
                logger.debug(s"Page ${p.uri}: Resolved link ${n.getDestination} to page ${t.uri}")
                val turi = t.uriWithSuffix(suffix)
                val turi2 = new URI(turi.getScheme, turi.getAuthority, turi.getPath, uri.getQuery, uri.getFragment)
                n.setDestination(Util.relativeSiteURI(p.uri, turi2).toString)
              case None =>
                logger.error(s"Page ${p.uri}: No page found for link ${n.getDestination}")
                n.setDestination(Util.relativeSiteURI(p.uri, uri).toString)
            }
          }
        }
        super.visit(n)
      }
    })
  }
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

class ExpandTocProcessor(toc: Vector[TocEntry]) extends PageProcessor {
  case class TocItem(text: Option[String], target: Option[String], children: Vector[TocItem]) {
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
    def log(prefix: String): Unit = {
      logger.debug(s"""$prefix- "${text.getOrElse("")}" -> ${target.getOrElse("")}""")
      children.foreach(_.log(prefix+"  "))
    }
  }

  /** Turn a `Section` recursively into a `TocItem` tree */
  def sectionToc(s: Section, p: Page, pTitle: Option[String], maxLevel: Int): Option[TocItem] = {
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
      val ch = s.children.flatMap(ch => sectionToc(ch, p, pTitle, maxLevel))
      if(title.isEmpty && target.isEmpty && ch.isEmpty) None
      else Some(TocItem(title, target, ch))
    }
  }

  /** Remove items without a title and move their children into the preceding item */
  def mergeHierarchies(items: Vector[TocItem]): Vector[TocItem] = {
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
  def mergePages(items: Vector[TocItem]): Vector[TocItem] = mergeHierarchies(items.flatMap { pageItem =>
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

  def apply(p: Page): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: CustomBlock): Unit = n match {
        case n: TocBlock =>
          val items: Vector[TocItem] =
            if(n.local) {
              val title = toc.find(_.page eq p) match {
                case Some(e) => Option(e.title)
                case None => p.section.title
              }
              sectionToc(p.section, p, title, n.maxLevel).toVector.flatMap(_.children)
            } else {
              val items = toc.flatMap(e => sectionToc(e.page.section, e.page, Option(e.title), n.maxLevel))
              if(n.mergeFirst) mergePages(items) else items
            }
          if(items.nonEmpty) {
            val ul = new BulletList
            ul.setTight(true)
            items.foreach { i =>
              i.log("")
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
