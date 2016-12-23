package com.novocode.ornate.commonmark

import org.commonmark.node._

abstract class TextProcessor extends AbstractVisitor {
  def transform(s: String, in: Node): String

  final def nullOrTransform(s: String, in: Node): String =
    if(s eq null) null else transform(s, in)

  override def visit(text: Text): Unit = {
    text.setLiteral(nullOrTransform(text.getLiteral, text))
    super.visit(text)
  }
  override def visit(link: Link): Unit = {
    link.setTitle(nullOrTransform(link.getTitle, link))
    super.visit(link)
  }
  override def visit(image: Image): Unit = {
    image.setTitle(nullOrTransform(image.getTitle, image))
    super.visit(image)
  }
}

object TextProcessor {
  def apply[N <: Node](n: N)(f: String => String): N = {
    val v = new TextProcessor {
      def transform(s: String, in: Node) = f(s)
    }
    n.accept(v)
    n
  }
}
