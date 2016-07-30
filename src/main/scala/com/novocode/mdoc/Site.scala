package com.novocode.mdoc

import java.net.URI
import java.nio.file.Path

import com.typesafe.config.Config
import org.commonmark.node._
import better.files._

class Site(val pages: Vector[Page], val toc: Toc)

class Page(val uri: URI, val doc: Node, val config: Config, val sections: Vector[Section],
           val extensions: Vector[AnyRef]) {
  override def toString: String = s"Page($uri)"

  val pathElements: Vector[String] = uri.getPath.split('/').filter(_.nonEmpty).to[Vector]

  def targetFile(base: File, ext: String = ""): File =
    (pathElements.init :+ (pathElements.last + ext)).foldLeft(base) { case (f, s) => f / s }

  def title: Option[String] =
    if(config.hasPath("title")) Some(config.getString("title"))
    else sections.headOption.map(_.title)
}

class Section(val id: String, val title: String, val heading: Heading, val children: Vector[Section]) {
  val level: Int = heading.getLevel

  override def toString = s"Section($id, $level, $title)"
}
