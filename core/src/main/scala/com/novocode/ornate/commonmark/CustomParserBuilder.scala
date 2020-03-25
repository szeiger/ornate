package com.novocode.ornate.commonmark

import java.io.Reader
import java.util.Arrays

import scala.jdk.CollectionConverters._
import org.commonmark._
import org.commonmark.internal._
import org.commonmark.internal.inline.{AsteriskDelimiterProcessor, UnderscoreDelimiterProcessor}
import org.commonmark.node.{Node, Text}
import org.commonmark.parser.{InlineParser, Parser, PostProcessor}
import org.commonmark.parser.block.BlockParserFactory
import org.commonmark.parser.delimiter.{DelimiterProcessor, DelimiterRun}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Allow customization of inline parsing by hooking into the parser internals. This can be
  * done cleanly once https://github.com/atlassian/commonmark-java/pull/69 is merged. */
class CustomParserBuilder extends Parser.Builder {
  private def get[T](name: String): T = {
    val f = classOf[Parser.Builder].getDeclaredField(name)
    f.setAccessible(true)
    f.get(this).asInstanceOf[T]
  }
  def _blockParserFactories = get[java.util.ArrayList[BlockParserFactory]]("blockParserFactories")
  def _delimiterProcessors = get[java.util.ArrayList[DelimiterProcessor]]("delimiterProcessors")
  def _postProcessors = get[java.util.ArrayList[PostProcessor]]("postProcessors")

  private[commonmark] var inlineDecorators = new mutable.ArrayBuffer[InlineParser => InlineParser]
  def addInlineDecorator(f: InlineParser => InlineParser): Unit = inlineDecorators += f
}

class CustomParser(builder: CustomParserBuilder) { // Cannot extend Parser because the constuctor is private
  val blockParserFactories = DocumentParser.calculateBlockParserFactories(builder._blockParserFactories)
  val delimiterProcessors = calculateDelimiterProcessors(builder._delimiterProcessors)
  val delimiterCharacters = InlineParserImpl.calculateDelimiterCharacters(delimiterProcessors.keySet)
  val specialCharacters = InlineParserImpl.calculateSpecialCharacters(delimiterCharacters)
  val postProcessors = builder._postProcessors

  def parse(input: String): Node = {
    val iipImpl = new InlineParserImpl(specialCharacters, delimiterCharacters, delimiterProcessors)
    def wrap(i: InlineParser): InlineParserImpl = new InlineParserImpl(null, null, null) {
      override def parse(input: String, node: Node): Unit = i.parse(input, node)
    }
    val inlineParser: InlineParserImpl =
      if(builder.inlineDecorators.isEmpty) iipImpl
      else wrap(builder.inlineDecorators.foldLeft(iipImpl: InlineParser) { case (z, n) => n(z) })
    val documentParser = new DocumentParser(blockParserFactories, inlineParser)
    val document = documentParser.parse(input)
    postProcessors.asScala.foldLeft(document: Node) { case (z, n) => n.process(z) }
  }

  def calculateDelimiterProcessors(dps: java.util.List[DelimiterProcessor]): java.util.Map[Character, DelimiterProcessor] = {
    val map = new mutable.HashMap[Character, DelimiterProcessor]
    for(dp <- new AsteriskDelimiterProcessor +: new UnderscoreDelimiterProcessor +: dps.asScala) {
      def add(ch: Character): Unit = {
        if(map contains ch)
          throw new IllegalArgumentException(s"Delimiter processor conflict with delimiter char '$ch'")
        map += ((ch, dp))
      }
      val opening = dp.getOpeningCharacter
      val closing = dp.getClosingCharacter
      if(opening == closing) {
        map.get(opening) match {
          case Some(old) if old.getOpeningCharacter == old.getClosingCharacter =>
            val staggered = old match {
              case s: StaggeredDelimiterProcessor => s
              case old =>
                val s = new StaggeredDelimiterProcessor(opening)
                s.add(old)
                s
            }
            staggered.add(dp)
            map += ((opening, staggered))
            // merge
          case _ => add(opening)
        }
      } else {
        add(opening)
        add(closing)
      }
    }
    map.asJava
  }
}

class StaggeredDelimiterProcessor(delim: Char) extends DelimiterProcessor {
  private[this] var minLength = 0
  private[this] var processors: List[DelimiterProcessor] = Nil // in reverse getMinLength order

  def getOpeningCharacter = delim
  def getClosingCharacter = delim
  def getMinLength: Int = minLength

  def add(dp: DelimiterProcessor): Unit = {
    val len = dp.getMinLength
    var added = false
    val b = new ListBuffer[DelimiterProcessor]
    for(p <- processors) {
      if(!added) {
        val pLen = p.getMinLength
        if(len > pLen) {
          b += dp
          added = true
        }
        else if(len == pLen)
          throw new IllegalArgumentException(s"Cannot add two delimiter processors for char '$delim' and minimum length $len")
      }
      b += p
    }
    if(!added) {
      b += dp
      minLength = len
    }
    processors = b.toList
  }

  private[this] def findProcessor(len: Int): DelimiterProcessor =
    processors.dropWhile(_.getMinLength > len).headOption.getOrElse(processors.head)

  def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    findProcessor(opener.length()).getDelimiterUse(opener, closer)

  def process(opener: Text, closer: Text, delimiterUse: Int): Unit =
    findProcessor(delimiterUse).process(opener, closer, delimiterUse)
}
