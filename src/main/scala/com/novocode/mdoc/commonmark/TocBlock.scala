package com.novocode.mdoc.commonmark

import org.commonmark.node.{CustomBlock, Visitor}

class TocBlock(var maxLevel: Int) extends CustomBlock {
  override def toStringAttributes = s"maxLevel=$maxLevel"
}
