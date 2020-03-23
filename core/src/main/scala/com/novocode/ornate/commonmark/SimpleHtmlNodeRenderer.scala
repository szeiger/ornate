package com.novocode.ornate.commonmark

import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.{HtmlNodeRendererContext, HtmlNodeRendererFactory}
import org.commonmark.node.Node

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

case class SimpleHtmlNodeRenderer[T <: Node : ClassTag, U](f: (T, HtmlNodeRendererContext) => U) extends HtmlNodeRendererFactory {
  def create(context: HtmlNodeRendererContext): NodeRenderer = new NodeRenderer {
    val getNodeTypes = Set[Class[_ <: Node]](implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[_ <: Node]]).asJava
    def render(n: Node): Unit = f(n.asInstanceOf[T], context)
  }
}
