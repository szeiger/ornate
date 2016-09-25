package com.novocode.ornate.commonmark

import com.novocode.ornate.Page
import org.commonmark.node.{FencedCodeBlock, AbstractVisitor}
import NodeExtensionMethods._

/** Replace FencedCodeBlocks with AttributedFencedCodeBlocks */
object AttributeFencedCodeBlocksProcessor extends PageProcessor {
  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    override def visit(n: FencedCodeBlock): Unit = if(!n.isInstanceOf[AttributedFencedCodeBlock]) {
      val a = new AttributedFencedCodeBlock
      a.setFenceChar(n.getFenceChar)
      a.setFenceIndent(n.getFenceIndent)
      a.setFenceLength(n.getFenceLength)
      a.setInfo(n.getInfo)
      a.setLiteral(n.getLiteral)
      n.children.foreach(ch => n.appendChild(ch))
      a.parseAttributes(n.getInfo)
      n.replaceWith(a)
    }
  })
}

class AttributedFencedCodeBlock extends FencedCodeBlock with Attributed
