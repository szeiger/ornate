package com.novocode.mdoc.commonmark

import java.util.regex.Pattern

import org.commonmark.Extension
import org.commonmark.node.{Block, BlockQuote}
import org.commonmark.parser._
import org.commonmark.parser.block._

import scala.collection.mutable.ListBuffer

object BlockQuoteAttributesExtension {
  def create: Extension = new BlockQuoteAttributesExtension
}

class BlockQuoteAttributesExtension extends Parser.ParserExtension {
  private val ATTRIBUTED = """\s*[^\\\\]?\{\s*([^\\}]*)\}\s*""".r
  private[this] val codeBlockIndent = 4

  def extend(parserBuilder: Parser.Builder) = parserBuilder.customBlockParserFactory(new Factory)

  private def isMarker(state: ParserState, index: Int): Boolean = {
    val line: CharSequence = state.getLine
    state.getIndent < codeBlockIndent && index < line.length && line.charAt(index) == '>'
  }

  private def isSpaceOrTab(s: CharSequence, index: Int) =
    index < s.length && (s.charAt(index) == ' ' || s.charAt(index) == '\t')

  class Factory extends AbstractBlockParserFactory {
    def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart = {
      val nextNonSpace = state.getNextNonSpaceIndex
      if(isMarker(state, nextNonSpace)) {
        val newColumn = {
          val c = state.getColumn + state.getIndent + 1
          if(isSpaceOrTab(state.getLine, nextNonSpace + 1)) c + 1 else c
        }
        val p = new Parser
        state.getLine.toString.substring(newColumn) match {
          case ATTRIBUTED(a) =>
            p.getBlock.parseAttributes(a)
            BlockStart.of(p).atColumn(state.getLine.length)
          case _ =>
            BlockStart.of(p).atColumn(newColumn)
        }
      } else BlockStart.none
    }
  }

  class Parser extends AbstractBlockParser {
    private val block: AttributedBlockQuote = new AttributedBlockQuote
    override def isContainer = true
    override def canContain(block: Block) = true
    def getBlock = block

    def tryContinue(state: ParserState): BlockContinue = {
      val nextNonSpace = state.getNextNonSpaceIndex
      if(isMarker(state, nextNonSpace)) {
        var newColumn = state.getColumn + state.getIndent + 1
        if(isSpaceOrTab(state.getLine, nextNonSpace + 1)) newColumn += 1
        BlockContinue.atColumn(newColumn)
      } else BlockContinue.none
    }
  }
}

class AttributedBlockQuote extends BlockQuote with Attributed {
  override protected def toStringAttributes = attributedToString
}
