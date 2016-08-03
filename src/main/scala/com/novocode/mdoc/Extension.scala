package com.novocode.mdoc

import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.parser.Parser.ParserExtension

import com.novocode.mdoc.commonmark.{AutoIdentifiersProcessor, PageProcessor}
import com.novocode.mdoc.config.Global

trait Extension {
  def pageProcessors(global: Global, site: Site): Seq[PageProcessor] = Nil
}

class Extensions(extensions: Vector[(String, Option[AnyRef])]) {
  override def toString: String = extensions.map {
    case (name, None) => s"($name)"
    case (name, Some(o)) =>
      val types = Seq(
        if(o.isInstanceOf[Extension]) Some("e") else None,
        if(o.isInstanceOf[ParserExtension]) Some("p") else None,
        if(o.isInstanceOf[HtmlRendererExtension]) Some("h") else None
      ).flatten
      if(types.isEmpty) name else types.mkString(s"$name[", ",", "]")
  }.mkString(", ")

  def parser: Vector[ParserExtension] =
    extensions.collect { case (_, Some(e: ParserExtension)) => e }

  def htmlRenderer: Vector[HtmlRendererExtension] =
    extensions.collect { case (_, Some(e: HtmlRendererExtension)) => e }

  def mdoc: Vector[Extension] =
    extensions.collect { case (_, Some(e: Extension)) => e }
}

/** Give all headings an ID so they can be linked to from the TOC and other places.
  * Otherwise only explicitly attributed headings get an ID. */
class AutoIdentifiersExtension extends Extension {
  override def pageProcessors(global: Global, site: Site) = Seq(new AutoIdentifiersProcessor(site))
}
