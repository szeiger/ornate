package com.novocode.ornate.commonmark

import NodeExtensionMethods.nodeToNodeExtensionMethods
import com.novocode.ornate.{Extension, Logging, Page, Site}
import com.novocode.ornate.config.ConfiguredObject
import org.commonmark.node.{Block, Image, Link, Node, Text}

import scala.collection.mutable.ArrayBuffer

/** Replace ASCII quotes by proper Unicode quotes in text content. */
class SmartQuotesExtension(co: ConfiguredObject) extends Extension {
  override def pageProcessors(site: Site): Seq[PageProcessor] = Seq(SmartQuotesProcessor)
}

object SmartQuotesProcessor extends PageProcessor with Logging {
  final val AsciiSingle = '\''
  final val AsciiDouble = '"'
  final val LeftSingle  = '\u2018'
  final val RightSingle = '\u2019'
  final val LeftDouble  = '\u201c'
  final val RightDouble = '\u201d'

  override def apply(p: Page): Unit = findSections(p.doc).foreach(s => processQuotes(identifyQuotes(s)))

  def findSections(n: Node): ArrayBuffer[ArrayBuffer[Section]] = {
    val res = new ArrayBuffer[ArrayBuffer[Section]]
    def collectTexts(n: Node, buf: ArrayBuffer[Section]): Unit = n match {
      case t: Text => if(t.getLiteral != null && t.getLiteral.nonEmpty) buf += new TextSection(t)
      case n => n.children.foreach(ch => collectTexts(ch, buf))
    }
    def findText(n: Node): Unit = n match {
      case b: Block if b.children.exists(_.isInstanceOf[Text]) =>
        val texts = new ArrayBuffer[Section]
        collectTexts(b, texts)
        res += texts
      case n =>
        n.children.foreach(findText)
    }
    def findOther(n: Node): Unit = n match {
      case l: Link => if(l.getTitle != null && l.getTitle.nonEmpty) res += ArrayBuffer(new LinkTitleSection(l))
      case i: Image => if(i.getTitle != null && i.getTitle.nonEmpty) res += ArrayBuffer(new ImageTitleSection(i))
      case n => n.children.foreach(findOther)
    }
    findText(n)
    findOther(n)
    res
  }

  def findPrevNonQuote(s: String, pos: Int): Option[Char] = {
    var i = pos-1
    while(i >= 0) {
      val c = s.charAt(i)
      if(c != AsciiSingle && c != AsciiDouble) return Some(c)
      i -= 1
    }
    None
  }

  def findNextNonQuote(s: String, pos: Int): Option[Char] = {
    val len = s.length
    var i = pos+1
    while(i < s.length) {
      val c = s.charAt(i)
      if(c != AsciiSingle && c != AsciiDouble) return Some(c)
      i += 1
    }
    None
  }

  def identifyQuotes(sections: ArrayBuffer[Section]): ArrayBuffer[Quote] = {
    val res = new ArrayBuffer[Quote]
    val slen = sections.length
    for(i <- 0 until slen) {
      val s = sections(i)
      val text = s.text
      val tlen = text.length
      val firstSection = i == 0
      val lastSection = i == slen-1
      for(j <- 0 until tlen) {
        val c = text.charAt(j)
        if(c == AsciiSingle || c == AsciiDouble) {
          // Non-Text parts are treated the same as a regular non-space character
          val left = findPrevNonQuote(text, j).orElse(if(firstSection) None else Some('x'))
          val right = findNextNonQuote(text, j).orElse(if(lastSection) None else Some('x'))
          res += new Quote(c, left, right, s, j)
        }
      }
    }
    res
  }

  def processQuotes(quotes: ArrayBuffer[Quote]): Unit = if(quotes.nonEmpty) {
    var stack: List[Quote] = Nil
    for(q <- quotes) {
      val canOpen = q.canOpen
      val canClose = q.canClose
      if(stack.nonEmpty && stack.head.ch == q.ch && canClose) {
        val open = stack.head
        stack = stack.tail
        open.setOpen()
        q.setClose()
      } else if(canOpen) {
        stack = q :: stack
      } else { // unbalanced quotes
        if(logger.isDebugEnabled()) logger.debug("Unbalanced quote: "+q)
        q.setUnbalanced()
      }
    }
    stack.foreach { q =>
      if(logger.isDebugEnabled()) logger.debug("Unbalanced quote: "+q)
      q.setUnbalanced()
    }
  }

  class Quote(private var _ch: Char, prevNonQuote: Option[Char], nextNonQuote: Option[Char], val section: Section, val pos: Int) {
    def ch: Char = _ch
    def ch_= (c: Char): Unit = if(c != _ch) {
      _ch = c
      val t = section.text
      section.text =
        if(pos == 0) String.valueOf(c) + t.substring(1)
        else if(pos == t.length-1) t.substring(0, pos) + c
        else t.substring(0, pos) + c + t.substring(pos+1)
    }
    def setOpen(): Unit = ch = (if(ch == AsciiSingle) LeftSingle else if(ch == AsciiDouble) LeftDouble else ch)
    def setClose(): Unit = ch = (if(ch == AsciiSingle) RightSingle else if(ch == AsciiDouble) RightDouble else ch)
    def setUnbalanced(): Unit = ch = (if(ch == AsciiSingle) RightSingle else ch)
    def canOpen = nextNonQuote.fold(false)(c => !Character.isWhitespace(c))
    def canClose = prevNonQuote.fold(false)(c => !Character.isWhitespace(c))
    override def toString = s"Quote($ch, $prevNonQuote, $nextNonQuote, $section, $pos)"
  }

  def processSections(sections: ArrayBuffer[Section]): Unit = {
    println("Section: "+sections)
  }

  sealed trait Section {
    def text: String
    def text_= (s: String): Unit
  }

  final class TextSection(t: Text) extends Section {
    def text = t.getLiteral
    def text_= (s: String): Unit = t.setLiteral(s)
    override def toString = "TextSection(\""+t.getLiteral+"\")"
  }

  final class LinkTitleSection(l: Link) extends Section {
    def text = l.getTitle
    def text_= (s: String): Unit = l.setTitle(s)
    override def toString = "LinkTitleSection(\""+l.getTitle+"\")"
  }

  final class ImageTitleSection(i: Image) extends Section {
    def text = i.getTitle
    def text_= (s: String): Unit = i.setTitle(s)
    override def toString = "ImageTitleSection(\""+i.getTitle+"\")"
  }
}
