package com.novocode.mdoc.commonmark

import java.net.URI
import java.util.Locale

import org.commonmark.node.{Paragraph, Image, HtmlBlock}

import scala.xml.XML

class HtmlBlockMatcher(elName: String) {
  case class Result(attrs: Map[String, String])

  def unapply(b: HtmlBlock): Option[Result] = {
    val lit = b.getLiteral.trim
    val s = lit.toLowerCase(Locale.ENGLISH)
    if(s.startsWith(s"<$elName>") || s.startsWith(s"<$elName ") || s.startsWith(s"<$elName/>")) {
      val xml = XML.loadString(lit)
      val attrs = xml.attributes.iterator.map(m => (m.prefixedKey.toLowerCase(Locale.ENGLISH), m.value.toString)).toMap
      Some(Result(attrs))
    } else None
  }
}

class SpecialImageMatcher(protocols: Set[String]) {
  class Result(val protocol: String, val image: Image) {
    def dest = image.getDestination
    lazy val uri = new URI(dest)
    def title = image.getTitle
  }

  def unapply(n: Image): Option[Result] = {
    val dest = n.getDestination
    val sep = dest.indexOf(':')
    if(sep > 0) {
      val proto = dest.substring(0, sep)
      if(protocols.contains(proto)) Some(new Result(proto, n)) else None
    } else None
  }
}

class SpecialImageParagraphMatcher(protocols: Set[String]) {
  val ImageMatcher = new SpecialImageMatcher(protocols)

  def unapply(n: Paragraph): Option[SpecialImageMatcher#Result] = {
    val f = n.getFirstChild
    if(f.getNext ne null) None
    else f match {
      case ImageMatcher(img) => Some(img)
      case _ => None
    }
  }
}
