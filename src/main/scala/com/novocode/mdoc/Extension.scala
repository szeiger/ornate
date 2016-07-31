package com.novocode.mdoc

import com.novocode.mdoc.commonmark.{AutoIdentifiersProcessor, PageProcessor}

trait Extension {
  def pageProcessors(global: Global, site: Site): Seq[PageProcessor] = Nil
}

/** Give all headings an ID so they can be linked to from the TOC and other places.
  * Otherwise only explicitly attributed headings get an ID. */
class AutoIdentifiersExtension extends Extension {
  override def pageProcessors(global: Global, site: Site) = Seq(new AutoIdentifiersProcessor(site))
}
