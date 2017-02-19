package com.novocode.ornate.commonmark

import NodeExtensionMethods._
import org.commonmark.node._
import org.commonmark.renderer.html.HtmlNodeRendererContext

class HtmlNodeRendererContextExtensionMethods(private val c: HtmlNodeRendererContext) {
  def renderChildren(n: Node): Unit = n.children.toVector.foreach(c.render)

  def renderInline(n: Node): Unit = {
    def render(n: Node): Unit = {
      if(n.isInstanceOf[Block]) n.children.foreach(render)
      else c.render(n)
    }
    render(n)
  }
}

object HtmlNodeRendererContextExtensionMethods {
  implicit def htmlNodeRendererContextToHtmlNodeRendererContextExtensionMethods(c: HtmlNodeRendererContext):
  HtmlNodeRendererContextExtensionMethods = new HtmlNodeRendererContextExtensionMethods(c)
}
