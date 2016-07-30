package com.novocode.mdoc

import java.util.Locale

import better.files._
import com.novocode.mdoc.commonmark.NodeExtensionMethods._
import com.novocode.mdoc.commonmark.{SpecialImageParagraphMatcher, HtmlBlockMatcher, TocBlock}

import org.commonmark.html.HtmlRenderer
import org.commonmark.node._
import org.commonmark.parser.Parser

import scala.io.Codec

import scala.xml.XML

object Main extends App {
  val startDir = "doc"

  val global = new Global(file"$startDir")

  val sources = global.sourceDir.collectChildren(_.name.endsWith("md"))
  val pages = sources.map { f => PageParser.parse(global, f) }.toVector
  val toc = Toc(global, pages)

  val SpecialObjectMatcher = new SpecialImageParagraphMatcher(Set("toctree"))
  val TocTreeMatcher = new HtmlBlockMatcher("toctree")
  pages.foreach { p =>
    p.doc.accept(new AbstractVisitor {
      override def visit(n: HtmlBlock): Unit = n match {
        case TocTreeMatcher(r) =>
          n.replaceWith(new TocBlock(r.attrs.get("maxlevel").map(_.toInt).getOrElse(global.tocMaxLevel)))
        case _ =>
      }
      override def visit(n: Paragraph): Unit = n match {
        case SpecialObjectMatcher(r) => r.protocol match {
          case "toctree" =>
            n.replaceWith(new TocBlock(r.attributes.get("maxlevel").map(_.toInt).getOrElse(global.tocMaxLevel)))
          case _ =>
        }
        case _ =>
      }
    })
  }

  val site = new Site(pages, toc)
  val theme = global.createTheme(site)
  theme.render
}
