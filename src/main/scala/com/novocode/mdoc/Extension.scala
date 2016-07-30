package com.novocode.mdoc

import com.novocode.mdoc.commonmark.PageProcessor

trait Extension {
  def pageProcessors: Seq[PageProcessor]
}

trait ExtensionFactory extends (Global => Extension)
