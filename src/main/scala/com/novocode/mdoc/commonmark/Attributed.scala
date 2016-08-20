package com.novocode.mdoc.commonmark

import scala.collection.mutable.{ListBuffer, StringBuilder, HashMap}

trait Attributed {
  var id: String = null
  val simpleAttrs: ListBuffer[String] = new ListBuffer
  val defAttrs: HashMap[String, String] = new HashMap

  private[commonmark] def parseAttributes(s: String): Unit = if(s ne null) {
    var i = 0
    var inDoubleQuotes, inSingleQuotes = false
    var current: StringBuilder = null
    def endToken(): Unit = if(current ne null) {
      val s = current.toString
      current = null
      val i = s.indexOf("=")
      if(i == -1) simpleAttrs += s
      else defAttrs += s.substring(0, i) -> s.substring(i+1)
    }
    def append(c: Char): Unit = {
      if(current eq null) current = new StringBuilder
      current.append(c)
    }
    while(i < s.length) {
      val c = s.charAt(i)
      c match {
        case ' ' | '\t' =>
          if(inDoubleQuotes || inSingleQuotes) append(c) else endToken()
        case '"' =>
          if(inSingleQuotes) append(c)
          else inDoubleQuotes = !inDoubleQuotes
        case '\'' =>
          if(inDoubleQuotes) append(c)
          else inSingleQuotes = !inSingleQuotes
        case _ => append(c)
      }
      i += 1
    }
    endToken()
  }

  protected def attributedToString: String = {
    val b = new StringBuilder()
    if(id ne null) b.append(s"id=$id ")
    if(simpleAttrs.nonEmpty) b.append(simpleAttrs.mkString("attrs=[", ", ", "] "))
    if(defAttrs.nonEmpty) b.append(defAttrs.map { case (k, v) => s"$k=$v" }.mkString("attrs=[", ", ", "] "))
    b.toString
  }
}

object Attributed {
  def parse(s: String): Attributed = {
    val a = new Attributed {}
    a.parseAttributes(s)
    a
  }
}