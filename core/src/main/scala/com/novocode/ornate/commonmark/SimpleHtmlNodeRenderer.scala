package com.novocode.ornate.commonmark

import org.commonmark.html.renderer.{NodeRendererContext, NodeRendererFactory, NodeRenderer}
import org.commonmark.node.Node

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

case class SimpleHtmlNodeRenderer[T <: Node : ClassTag, U](f: (T, NodeRendererContext) => U) extends NodeRendererFactory {
  def create(context: NodeRendererContext): NodeRenderer = new NodeRenderer {
    val getNodeTypes = Set[Class[_ <: Node]](implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[_ <: Node]]).asJava
    def render(n: Node): Unit = f(n.asInstanceOf[T], context)
  }
}
