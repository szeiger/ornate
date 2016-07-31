package com.novocode.mdoc.commonmark

import NodeExtensionMethods._

import org.commonmark.node._

class NodeExtensionMethods(private val node: Node) extends AnyVal {

  def children: Iterator[Node] = new Iterator[Node] {
    private[this] var n = node.getFirstChild
    def hasNext = n ne null
    def next() = {
      if(n eq null) throw new NoSuchElementException
      val res = n
      n = n.getNext
      res
    }
  }

  def dumpDoc(prefix: String = ""): Unit = {
    println(prefix + node)
    children.foreach(ch => ch.dumpDoc(prefix + "  "))
  }

  def compute[T](a: NodeAccumulator[T]): T = {
    node.accept(a)
    a.result
  }

  def replaceWith(n: Node): Node = {
    node.insertBefore(n)
    node.unlink()
    n
  }
}

object NodeExtensionMethods {
  implicit def nodeToNodeExtensionMethods(n: Node): NodeExtensionMethods = new NodeExtensionMethods(n)
}
