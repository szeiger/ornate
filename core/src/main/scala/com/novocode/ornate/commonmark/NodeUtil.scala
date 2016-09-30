package com.novocode.ornate.commonmark

import org.commonmark.node._

import scala.util.control.ControlThrowable

object NodeUtil {
  import NodeExtensionMethods._

  def findHeadings(n: Node): Vector[Heading] = n match {
    case h: Heading => Vector(h)
    case n => n.children.toVector.flatMap(findHeadings)
  }

  def extractText(n: Node, withCodeBlocks: Boolean = true, withFirstHeading: Boolean = true, limit: Int = -1): String = {
    var firstHeadingSeen = false
    val b = new StringBuilder
    case object Done extends ControlThrowable
    def append(s: String): Unit = {
      b.append(s)
      if(limit > 0 && b.length >= limit) throw Done
    }
    def f(n: Node): Unit = n match {
      case n: Heading if !firstHeadingSeen && !withFirstHeading => firstHeadingSeen = true
      case n: Text => append(n.getLiteral)
      case n: Code => append(n.getLiteral)
      case n: SoftLineBreak => append("\n")
      case n: HardLineBreak => append("\n")
      case n: IndentedCodeBlock if withCodeBlocks => append(n.getLiteral)
      case n: FencedCodeBlock if withCodeBlocks => append(n.getLiteral)
      case _: Paragraph | _: Heading | _: ThematicBreak =>
        if(!b.isEmpty && !b.endsWith("\n")) append("\n")
        n.children.foreach(f)
        if(!b.isEmpty && !b.endsWith("\n")) append("\n")
      case n => n.children.foreach(f)
    }
    try f(n) catch { case Done => }
    b.toString
  }
}
