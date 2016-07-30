package com.novocode.mdoc.commonmark

import org.commonmark.node.{CustomBlock, Visitor}

class TocBlock(var maxLevel: Int, var title: String) extends CustomBlock {
  override def toStringAttributes = {
    val b = new StringBuilder
    b.append(s"maxLevel=$maxLevel")
    if(title ne null) b.append(s", title=$title")
    b.toString
  }
}
