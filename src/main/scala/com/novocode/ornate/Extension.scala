package com.novocode.ornate

import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.parser.Parser.ParserExtension

import com.novocode.ornate.commonmark.PageProcessor
import com.novocode.ornate.config.Global

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

  def ornate: Vector[Extension] =
    extensions.collect { case (_, Some(e: Extension)) => e }
}
