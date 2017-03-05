package com.novocode.ornate

import com.novocode.ornate.config.ConfiguredObject
import com.novocode.ornate.config.Global
import com.novocode.ornate.config.ReferenceConfig
import com.typesafe.config.Config
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.html.HtmlRenderer.HtmlRendererExtension

trait Extension {
  def preProcessors(pageConfig: Config): Seq[PreProcessor] = Nil
  def pageProcessors(site: Site): Seq[PageProcessor] = Nil
  def parserExtensions(pageConfig: Config): Seq[ParserExtension] = Nil
  def htmlRendererExtensions: Seq[HtmlRendererExtension] = Nil
}

class Extensions(extensions: Vector[(ConfiguredObject, Option[AnyRef])]) {
  override def toString: String = extensions.map {
    case (co, None) => s"(${co.name})"
    case (co, Some(o)) =>
      val types = Seq(
        if(o.isInstanceOf[Extension]) Some("e") else None,
        if(o.isInstanceOf[ParserExtension]) Some("p") else None,
        if(o.isInstanceOf[HtmlRendererExtension]) Some("h") else None
      ).flatten
      if(types.isEmpty) co.name else types.mkString(s"${co.name}[", ",", "]")
  }.mkString(", ")

  def parser(pageConfig: Config): Vector[ParserExtension] =
    extensions.flatMap {
      case (_, Some(e: ParserExtension)) => Vector(e)
      case (_, Some(e: Extension)) => e.parserExtensions(pageConfig)
      case _ => None
    }

  def htmlRenderer: Vector[HtmlRendererExtension] =
    extensions.flatMap {
      case (_, Some(e: HtmlRendererExtension)) => Vector(e)
      case (_, Some(e: Extension)) => e.htmlRendererExtensions
      case _ => None
    }

  def ornate: Vector[Extension] =
    extensions.collect { case (_, Some(e: Extension)) => e }
}
