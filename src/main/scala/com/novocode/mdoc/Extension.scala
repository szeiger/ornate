package com.novocode.mdoc

import com.novocode.mdoc.commonmark.PageProcessor

trait Extension {
  def pageProcessors(global: Global, site: Site): Seq[PageProcessor]
}
