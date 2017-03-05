package com.novocode.ornate.commonmark

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlNodeRendererFactory

case class SimpleHtmlNodeRenderer[T <: Node : ClassTag, U](f: (T, HtmlNodeRendererContext) => U) extends HtmlNodeRendererFactory {
  def create(context: HtmlNodeRendererContext): NodeRenderer = new NodeRenderer {
    val getNodeTypes = Set[Class[_ <: Node]](implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[_ <: Node]]).asJava
    def render(n: Node): Unit = f(n.asInstanceOf[T], context)
  }
}
