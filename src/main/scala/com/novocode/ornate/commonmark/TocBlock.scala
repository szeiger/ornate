package com.novocode.ornate.commonmark

import org.commonmark.node.{CustomBlock, Visitor}

class TocBlock(var maxLevel: Int, var mergeFirst: Boolean, var local: Boolean, var focusMaxLevel: Int) extends CustomBlock {
  var title: String = null
  override def toStringAttributes = {
    val b = new StringBuilder
    b.append(s"maxLevel=$maxLevel")
    if(title ne null) b.append(s", title=$title")
    b.append(s", mergeFirst=$mergeFirst")
    b.append(s", local=$local")
    b.append(s", focusMaxLevel=$focusMaxLevel")
    b.toString
  }
}
