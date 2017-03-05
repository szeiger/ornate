package com.novocode.ornate

import scala.collection.mutable

import com.novocode.ornate.commonmark.Attributed
import com.novocode.ornate.commonmark.AttributedFencedCodeBlock
import com.novocode.ornate.commonmark.NodeExtensionMethods
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.config.ConfiguredObject
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Text

/** Expand variables in text content and fenced code blocks. */
class ExpandVarsExtension(co: ConfiguredObject) extends Extension {
  override def pageProcessors(site: Site) = Seq(new ExpandVarsProcessor(co))
}

class ExpandVarsProcessor(co: ConfiguredObject) extends PageProcessor with Logging {
  def runAt: Phase = Phase.Expand

  def apply(p: Page): Unit = p.doc.accept(new AbstractVisitor {
    val config = co.getConfig(p.config)
    val inCode = config.getBoolean("code")
    val inIndentedCodeBlock = config.getBoolean("indentedCodeBlock")
    val inFencedCodeBlock = config.getBoolean("fencedCodeBlock")
    val inText = config.getBoolean("text")
    val startDelim = config.getString("startDelimiter").trim
    val endDelim = config.getString("endDelimiter").trim

    override def visit(n: FencedCodeBlock): Unit = {
      val attr = n match {
        case n: AttributedFencedCodeBlock => n
        case n => Attributed.parse(n.getInfo)
      }
      if(attr.defAttrs.get("expandVars").map(_.toBoolean).getOrElse(inFencedCodeBlock))
        n.setLiteral(expandIn(n.getLiteral))
      super.visit(n)
    }
    override def visit(n: Code): Unit = {
      if(inCode) n.setLiteral(expandIn(n.getLiteral))
      super.visit(n)
    }
    override def visit(n: IndentedCodeBlock): Unit = {
      if(inCode) n.setLiteral(expandIn(n.getLiteral))
      super.visit(n)
    }
    override def visit(n: Text): Unit = {
      if(inCode) n.setLiteral(expandIn(n.getLiteral))
      super.visit(n)
    }

    def expandIn(s: String): String = {
      var start = s.indexOf(startDelim)
      if(start < 0) s else {
        val starts = new mutable.ArrayBuffer[Int]
        while(start >= 0) {
          starts.append(start)
          start = s.indexOf(startDelim, start+startDelim.length)
        }
        val repl = starts.iterator.zip(starts.iterator.drop(1) ++ Iterator(-1)).toSeq.flatMap { case (start, next) =>
          val end = s.indexOf(endDelim, start+startDelim.length)
          if(end >= 0 && (end < next || next == -1) && !s.substring(start+startDelim.length, end).contains("\n")) {
            val key = s.substring(start+startDelim.length, end).trim
            try Seq((start, end+endDelim.length, p.config.getString(key))) catch { case ex: Exception =>
              logger.error(s"""Page ${p.uri}: Error expanding config reference "$key"""", ex)
              Seq.empty
            }
          }
          else Seq.empty
        }
        val res = new mutable.StringBuilder
        var copied = 0
        var len = s.length
        repl.foreach { case (start, end, value) =>
          if(start > copied) res.append(s.substring(copied, start))
          res.append(value)
          copied = end
        }
        if(s.length > copied) res.append(s.substring(copied))
        res.toString
      }
    }
  })
}
