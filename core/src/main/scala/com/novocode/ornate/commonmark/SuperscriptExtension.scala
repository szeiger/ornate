package com.novocode.ornate.commonmark

import NodeExtensionMethods.nodeToNodeExtensionMethods
import com.novocode.ornate.{Extension, Page, Site}
import com.novocode.ornate.config.{ConfiguredObject, Global}
import com.typesafe.config.Config
import org.commonmark.node._
import org.commonmark.parser.Parser
import org.commonmark.parser.delimiter.{DelimiterProcessor, DelimiterRun}
import org.commonmark.renderer.html.{HtmlNodeRendererContext, HtmlRenderer}

import scala.reflect.ClassTag

/** Parse superscript notation, delimited by `^`, with no other markup or unescaped whitespace in between. */
class SuperscriptExtension(co: ConfiguredObject) extends SuperscriptSubscriptExtension[Superscript] {
  def delim = '^'
  def create(): Node = new Superscript
  def tag = "sup"
}

/** Parse superscript notation, delimited by `~`, with no other markup or unescaped whitespace in between. */
class SubscriptExtension(co: ConfiguredObject) extends SuperscriptSubscriptExtension[Subscript] {
  def delim = '~'
  def create(): Node = new Subscript
  def tag = "sub"
}

abstract class SuperscriptSubscriptExtension[T <: Node : ClassTag] extends Extension {
  def delim: Char
  def create(): Node
  def tag: String
  val ext = Seq(new Parser.ParserExtension {
    override def extend(builder: Parser.Builder): Unit =
      builder.customDelimiterProcessor(new SuperscriptSubscriptDelimiterProcessor(delim)(create))
  })
  override def parserExtensions(pageConfig: Config) = ext
  override val htmlRendererExtensions: Seq[HtmlRenderer.HtmlRendererExtension] = Seq(
    new HtmlRenderer.HtmlRendererExtension {
      override def extend(builder: HtmlRenderer.Builder): Unit =
        builder.nodeRendererFactory(SimpleHtmlNodeRenderer { (n: T, c: HtmlNodeRendererContext) =>
          val wr = c.getWriter
          wr.tag(tag)
          n.children.foreach(c.render)
          wr.tag("/"+tag)
        })
    }
  )
}

class SuperscriptSubscriptDelimiterProcessor(delim: Char)(create: () => Node) extends DelimiterProcessor {
  override def getMinLength = 1
  override def getOpeningCharacter = delim
  override def getClosingCharacter = delim

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    if(opener.length == 1 && closer.length == 1) 1 else 0

  def containsUnescapedSpace(s: String): Boolean = {
    val len = s.length
    var i = 0
    while(i < len) {
      val c = s.charAt(i)
      if(c == '\\') i += 1
      else if(c == ' ') return true
      i += 1
    }
    false
  }

  def unescapeSpaces(s: String): String = {
    val len = s.length
    val b = new StringBuilder(len)
    var i = 0
    while(i < len) {
      val c = s.charAt(i)
      if(c == '\\' && i+1 < len) {
        i += 1
        val c2 = s.charAt(i)
        if(c2 != ' ') b.append(c)
        b.append(c2)
      } else b.append(c)
      i += 1
    }
    b.result()
  }

  override def process(opener: Text, closer: Text, delimiterUse: Int): Unit = {
    val between = Iterator.iterate(opener.getNext)(n => if(n eq null) null else n.getNext).takeWhile(_ ne closer).toList
    between match {
      case (t: Text) :: Nil if opener.getLiteral.isEmpty && closer.getLiteral.isEmpty && !containsUnescapedSpace(t.getLiteral) =>
        val wrap = create()
        wrap.appendChild(t)
        t.setLiteral(unescapeSpaces(t.getLiteral))
        opener.insertAfter(wrap)
      case _ =>
        opener.insertAfter(new Text(String.valueOf(delim)))
        closer.insertBefore(new Text(String.valueOf(delim)))
    }
  }
}

class Superscript extends CustomNode with Delimited {
  def getOpeningDelimiter = "^"
  def getClosingDelimiter = "^"
}

class Subscript extends CustomNode with Delimited {
  def getOpeningDelimiter = "~"
  def getClosingDelimiter = "~"
}
