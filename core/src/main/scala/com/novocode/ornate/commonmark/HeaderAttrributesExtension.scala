package com.novocode.ornate.commonmark

import scala.collection.mutable.ListBuffer

import java.util.regex.Pattern

import org.commonmark.Extension
import org.commonmark.node.Heading
import org.commonmark.parser._
import org.commonmark.parser.block._

object HeaderAttributesExtension {
  def create: Extension = new HeaderAttributesExtension
}

class HeaderAttributesExtension extends Parser.ParserExtension {
  private val ATX_HEADING = "^#{1,6}(?: +|$)".r.pattern
  private val ATX_TRAILING = "(^| ) *#+ *$".r.pattern
  private val SETEXT_HEADING = "^(?:=+|-+) *$".r.pattern
  private val ATTRIBUTED = """(.*)[^\\\\]?\{\s*([^\\}]*)\}\s*""".r

  def extend(parserBuilder: Parser.Builder) = parserBuilder.customBlockParserFactory(new Factory)

  class Factory extends AbstractBlockParserFactory {
    def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart = {
      if(state.getIndent >= 4) BlockStart.none
      else {
        val line = state.getLine
        val nextNonSpace = state.getNextNonSpaceIndex
        val paragraph = matchedBlockParser.getParagraphContent
        val matcher = ATX_HEADING.matcher(line.subSequence(nextNonSpace, line.length))
        if(matcher.find) {
          val newOffset = nextNonSpace + matcher.group(0).length
          val level = matcher.group(0).trim.length
          val content = ATX_TRAILING.matcher(line.subSequence(newOffset, line.length)).replaceAll("")
          BlockStart.of(new Parser(level, content)).atIndex(line.length)
        } else {
          val matcher = SETEXT_HEADING.matcher(line.subSequence(nextNonSpace, line.length))
          if((paragraph ne null) && matcher.find) {
            val level = if (matcher.group(0).charAt(0) == '=') 1 else 2
            val content = paragraph.toString
            BlockStart.of(new Parser(level, content)).atIndex(line.length).replaceActiveBlockParser
          } else BlockStart.none
        }
      }
    }
  }

  class Parser(level: Int, content: String) extends AbstractBlockParser {
    private[this] val block = new AttributedHeading
    block.setLevel(level)
    def getBlock = block
    def tryContinue(parserState: ParserState): BlockContinue = BlockContinue.none
    override def parseInlines(inlineParser: InlineParser) = {
      content match {
        case ATTRIBUTED(c, a) =>
          block.parseAttributes(a)
          inlineParser.parse(c.trim, block)
        case _ =>
          inlineParser.parse(content, block)
      }
    }
  }
}

class AttributedHeading extends Heading with Attributed {
  override protected def toStringAttributes = s"level=$getLevel $attributedToString"
}
