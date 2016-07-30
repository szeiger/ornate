package com.novocode.mdoc

import java.util.Locale

object Util {
  def createIdentifier(text: String) = {
    val s = text.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "-").filter {
      case '_' | '-' | '.' => true
      case c if Character.isDigit(c) || Character.isAlphabetic(c) => true
      case _ => false
    }.dropWhile(c => !Character.isAlphabetic(c))
    if(s.nonEmpty) s else "section"
  }
}
