package com.novocode.ornate.commonmark

import com.novocode.ornate.{Page, PageProcessor, Phase}
import org.commonmark.node.{AbstractVisitor, FencedCodeBlock}
import NodeExtensionMethods._

import scala.collection.mutable

/** Replace FencedCodeBlocks with AttributedFencedCodeBlocks */
object AttributeFencedCodeBlocksProcessor extends PageProcessor {
  def runAt: Phase = Phase.Attribute

  def lift(n: FencedCodeBlock): AttributedFencedCodeBlock = n match {
    case n: AttributedFencedCodeBlock => n
    case n =>
      val a = new AttributedFencedCodeBlock
      a.setFenceChar(n.getFenceChar)
      a.setFenceIndent(n.getFenceIndent)
      a.setFenceLength(n.getFenceLength)
      a.setInfo(n.getInfo)
      a.setLiteral(n.getLiteral)
      n.children.foreach(ch => n.appendChild(ch))
      a.parseAttributes(n.getInfo)
      n.replaceWith(a)
      a
  }

  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    override def visit(n: FencedCodeBlock): Unit = lift(n)
  })
}

class AttributedFencedCodeBlock extends FencedCodeBlock with Attributed {
  val postHighlightSubstitutions = new mutable.ArrayBuffer[String]
  var noHighlight = false

  private var _prefix: String = null
  private var _nextId = 0

  def getLanguage: Option[String] =
    if(getInfo eq null) None else getInfo.split(' ').find(_.nonEmpty)

  def substitutionPrefix: String = {
    if(_prefix eq null) _prefix = unusedIdentifierPrefix(getLiteral)
    _prefix
  }

  def nextSubstitutionId(): String = {
    val id = substitutionPrefix + toLetters(_nextId)
    _nextId += 1
    id
  }

  private def unusedIdentifierPrefix(in: String): String = {
    val start = "subst"
    if(!in.contains(start)) start
    else 0.to(Int.MaxValue-1).iterator.map(i => start + toLetters(i)).find(s => !in.contains(s)).get
  }

  private def toLetters(i: Int): String = {
    val r = i % 26
    val digit = ('A' + r).toChar
    val d = i / 26
    if(d == 0) String.valueOf(digit) else toLetters(d) + digit
  }

  def splitProcessed(_s: String): Seq[Either[String, Int]] = {
    if(postHighlightSubstitutions.isEmpty) Seq(Left(_s)) else {
      val pre = substitutionPrefix
      val res = new mutable.ArrayBuffer[Either[String, Int]]
      var s = _s
      var i = s.indexOf(pre)
      while(i >= 0) {
        if(i > 0) res += Left(s.substring(0, i))
        s = s.substring(i)
        val start = postHighlightSubstitutions.indexWhere(id => s.startsWith(id)) match {
          case -1 => 1 // Unexpected prefix that didn't match an ID
          case idx =>
            res += Right(idx)
            s = s.substring(postHighlightSubstitutions(idx).length)
            0
        }
        i = s.indexOf(pre, start)
      }
      if(s.length > 0) res += Left(s)
      res.toSeq
    }
  }
}
