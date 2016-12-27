package com.novocode.ornate.commonmark

import com.novocode.ornate._
import com.novocode.ornate.config.ConfiguredObject

/** Replace ASCII punctuation by Unicode em-dashes, en-dashes and ellipses in text content. */
class SmartPunctuationExtension(co: ConfiguredObject) extends Extension {
  override def pageProcessors(site: Site): Seq[PageProcessor] = Seq(SmartPunctuationProcessor)
}

object SmartPunctuationProcessor extends PageProcessor {
  def runAt: Phase = Phase.Visual
  override def apply(p: Page): Unit = TextProcessor(p.doc) { s =>
    s.replace("---", "\u2014") // em-dash
      .replace("--", "\u2013") // en-dash
      .replace("...", "\u2026") // ellipsis
  }
}
