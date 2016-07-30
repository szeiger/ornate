package com.novocode.mdoc.commonmark

import org.commonmark.node._

import scala.collection.mutable.ListBuffer

abstract class NodeAccumulator[T] extends AbstractVisitor {
  def result: T
}

final class HeadingAccumulator extends NodeAccumulator[List[Heading]] {
  private[this] var b = new ListBuffer[Heading]
  def result: List[Heading] = b.toList
  override def visit(h: Heading): Unit = b.append(h)
}

final class TextAccumulator extends NodeAccumulator[String] {
  private[this] val b: StringBuilder = new StringBuilder
  def result: String = b.toString
  override def visit(n: Text): Unit = b.append(n.getLiteral)
  override def visit(n: SoftLineBreak): Unit = b.append('\n')
  override def visit(n: HardLineBreak): Unit = b.append('\n')
}
