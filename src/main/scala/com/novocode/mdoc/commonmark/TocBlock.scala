package com.novocode.mdoc.commonmark

import org.commonmark.node.{CustomBlock, Visitor}

class TocBlock(var maxLevel: Int, var title: String, var mergeFirst: Boolean, var local: Boolean) extends CustomBlock {
  override def toStringAttributes = {
    val b = new StringBuilder
    b.append(s"maxLevel=$maxLevel")
    if(title ne null) b.append(s", title=$title")
    b.append(s", mergeFirst=$mergeFirst")
    b.append(s", local=$local")
    b.toString
  }
}
