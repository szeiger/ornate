package com.novocode.ornate

import com.novocode.ornate.commonmark.AttributeFencedCodeBlocksProcessor
import org.commonmark.node._

/** Set the `noHighlight` flag of fenced code blocks with the specified language codes */
class NoHighlightProcessor(langs: Set[String]) extends PageProcessor {
  def runAt: Phase = Phase.PreHighlight

  def apply(p: Page): Unit = if(langs.nonEmpty) {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: FencedCodeBlock): Unit = {
        val a = AttributeFencedCodeBlocksProcessor.lift(n)
        a.getLanguage match {
          case Some(lang) if langs.contains(lang) => a.noHighlight = true
          case _ =>
        }
      }
    })
  }
}
