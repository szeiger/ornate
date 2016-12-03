package com.novocode.ornate.commonmark

import com.novocode.ornate.EmojiParserExtension.{EmojiImage, EmojiUnicode, PlainText}
import com.novocode.ornate.{Extension, Logging}
import com.novocode.ornate.commonmark.NodeExtensionMethods._
import com.novocode.ornate.config.ConfiguredObject
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.typesafe.config.Config
import org.commonmark.node._
import org.commonmark.parser.block._
import org.commonmark.parser.delimiter.{DelimiterProcessor, DelimiterRun}
import org.commonmark.parser.{InlineParser, Parser, PostProcessor}
import play.twirl.api.HtmlFormat

import scala.collection.mutable
import scala.xml.XML

/** Parse inline and block math notation. */
class MathSyntaxExtension(co: ConfiguredObject) extends Extension {
  override def parserExtensions(pageConfig: Config) = Seq(new MathSyntaxParserExtension(co, pageConfig))
}

class MathSyntaxParserExtension(co: ConfiguredObject, pageConfig: Config) extends Parser.ParserExtension with Logging {
  val htmlPrefix = "<inline-math-syntax lang=\""

  val dollarInline     = co.getConfig(pageConfig).getStringOr("dollarInline")
  val dollarBlock      = co.getConfig(pageConfig).getStringOr("dollarBlock")
  val singleBackslash  = co.getConfig(pageConfig).getStringOr("singleBackslash")
  val doubleBackslash  = co.getConfig(pageConfig).getStringOr("doubleBackslash")
  val dollarInlineCode = co.getConfig(pageConfig).getStringOr("dollarInlineCode")

  val anyBackslash = (singleBackslash ne null) || (doubleBackslash ne null)

  def extend(builder: Parser.Builder): Unit = {
    if(dollarInline != null || singleBackslash != null || doubleBackslash != null) { // Add inline parsers
      builder.asInstanceOf[CustomParserBuilder].addInlineDecorator { i => new InlineParser {
        def parse(input: String, node: Node): Unit = i.parse(processInline(input), node)
      }}
      builder.postProcessor(new PostProcessor {
        def process(node: Node): Node = {
          node.accept(new AbstractVisitor {
            override def visit(n: HtmlInline): Unit = {
              val lit = n.getLiteral
              if(lit.startsWith(htmlPrefix)) {
                val x = XML.loadString(n.getLiteral)
                val im = new InlineMath
                im.language = x.attribute("lang").get.apply(0).text
                im.literal = x.attribute("code").get.apply(0).text
                n.insertBefore(im)
                n.unlink()
              }
            }
            override def visit(n: Code): Unit = {
              def extractCode(s: String): String = {
                val x = XML.loadString(s)
                x.attribute("code").get.apply(0).text
              }
              def decode(s: String): String = {
                val start = s.indexOf(htmlPrefix)
                if(start == -1) s
                else {
                  val end = s.indexOf("/>", htmlPrefix.length)
                  if(end == -1) {
                    logger.warn("Unterminated <inline-math-syntax> element. This indicates a nesting error with mixed math and inline code notation.")
                    s
                  } else s.substring(0, start) + "$" + extractCode(s.substring(start, end+2)) + "$" + decode(s.substring(end+2))
                }
              }
              n.setLiteral(decode(n.getLiteral))
            }
          })
          node
        }
      })
    }
    // Add block parsers
    if(dollarBlock != null)
      builder.customBlockParserFactory(new MathBlockParserFactory("$$", "$$", dollarBlock))
    if(doubleBackslash != null)
      builder.customBlockParserFactory(new MathBlockParserFactory("\\\\[", "\\\\]", doubleBackslash))
    if(singleBackslash != null)
      builder.customBlockParserFactory(new MathBlockParserFactory("\\[", "\\]", singleBackslash))
    // Add inline code postprocessor
    if(dollarInlineCode != null) {
      builder.postProcessor(new PostProcessor {
        def process(node: Node): Node = {
          node.accept(new AbstractVisitor {
            override def visit(n: Code): Unit = {
              val s = n.getLiteral
              if(s.length > 2 && s.charAt(0) == '$' && s.charAt(s.length-1) == '$') {
                val im = new InlineMath
                im.literal = s.substring(1, s.length-1)
                im.language = dollarInlineCode
                n.replaceWith(im)
              } else if(s.length > 3 && s.startsWith("\\$") && s.charAt(s.length-1) == '$') {
                n.setLiteral(s.substring(1))
              }
            }
          })
          node
        }
      })
    }
  }

  class MathBlockParserFactory(startDelim: String, endDelim: String, lang: String) extends AbstractBlockParserFactory {
    def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart = {
      if(state.getLine.toString.trim.startsWith(startDelim)) {
        val parser = new AbstractBlockParser {
          val literal = new mutable.StringBuilder()
          var endSeen = false
          val block = new MathBlock
          block.language = lang
          def getBlock: Block = block
          def tryContinue(state: ParserState): BlockContinue =
            if(endSeen) BlockContinue.none() else BlockContinue.atIndex(state.getIndex)
          override def addLine(line: CharSequence): Unit = {
            val s = line.toString
            if(isEnd(s.trim)) {
              endSeen = true
              literal.append(s.substring(0, s.lastIndexOf(endDelim)))
            } else literal.append(s).append('\n')
          }
          override def closeBlock(): Unit = block.literal = literal.toString
          def isEnd(trimmed: String): Boolean =
            trimmed.endsWith(endDelim) && !trimmed.endsWith("\\"+endDelim)
        }
        BlockStart.of(parser).atIndex(state.getNextNonSpaceIndex + startDelim.length)
      } else BlockStart.none
    }
  }

  def encodeAsHtml(s: String, lang: String): String = htmlPrefix+lang+"\" code=\""+HtmlFormat.escape(s)+"\" />"

  /** Encode inline math sequences into intermediate inline HTML. */
  def processInline(s: String): String = {
    val len = s.length
    var i = s.indexOf('$')
    if(i == -1 || i == len-1) s else {
      val b = new mutable.StringBuilder(s.length + 128)
      b.append(s.substring(0, i))
      var start = -1 // set to the pos after the delimiter
      var delim = 0 // dollar = 1, singleBackslash = 2, doubleBackslash = 3 (doubles as the length of the delimiter)
      def isDollarStart = (i == 0 || s.charAt(i-1) != '\\') && i < len-1 && !Character.isWhitespace(s.charAt(i+1))
      def isDollarEnd = !Character.isWhitespace(s.charAt(i-1)) && s.charAt(i-1) != '\\' && (i == len-1 || !Character.isDigit(s.charAt(i+1)))
      def isSingleBackslashDelim = i >= 1 && s.charAt(i-1) == '\\' && (i == 1 || s.charAt(i-2) != '\\')
      def isDoubleBackslashDelim = i >= 2 && s.charAt(i-1) == '\\' && s.charAt(i-2) == '\\' && (i == 2 || s.charAt(i-3) != '\\')
      while(i < len) {
        s.charAt(i) match {
          case '$' if dollarInline ne null =>
            if(start == -1) { // check for start
              if(isDollarStart) {
                start = i+1
                delim = 1
              } else b.append('$')
            } else if(delim == 1 && isDollarEnd) { // check for end
              if(i == start) b.append("$$") // no empty blocks allowed
              else b.append(encodeAsHtml(s.substring(start, i), dollarInline))
              start = -1
            }
          case '(' if anyBackslash && start == -1 =>
            if((doubleBackslash ne null) && isDoubleBackslashDelim) {
              start = i+1
              delim = 3
            } else if((singleBackslash ne null) && isSingleBackslashDelim) {
              start = i+1
              delim = 2
            } else b.append('(')
          case ')' if anyBackslash && start != -1 =>
            if(delim == 3 && isDoubleBackslashDelim) {
              b.append(encodeAsHtml(s.substring(start, i+2), doubleBackslash))
              start = -1
            } else if(delim == 2 && isSingleBackslashDelim) {
              b.append(encodeAsHtml(s.substring(start, i+1), singleBackslash))
              start = -1
            }
          case c =>
            if(start == -1) b.append(c)
        }
        i += 1
      }
      if(start != -1) b.append(s.substring(start-delim)) // unterminated sequence
      b.toString
    }
  }
}

trait MathNode { this: Node =>
  var literal: String = null
  var language: String = null
  def isBlock: Boolean = this.isInstanceOf[Block]

  override protected def toStringAttributes: String = s"literal=$literal, language=$language"
}

class MathBlock extends CustomBlock with MathNode

class InlineMath extends CustomNode with MathNode
