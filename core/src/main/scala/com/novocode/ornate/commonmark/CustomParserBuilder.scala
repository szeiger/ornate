package com.novocode.ornate.commonmark

import java.io.Reader

import scala.collection.JavaConverters._
import org.commonmark._
import org.commonmark.internal._
import org.commonmark.node.Node
import org.commonmark.parser.{InlineParser, Parser, PostProcessor}
import org.commonmark.parser.block.BlockParserFactory
import org.commonmark.parser.delimiter.DelimiterProcessor

import scala.collection.mutable

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
  val delimiterProcessors = InlineParserImpl.calculateDelimiterProcessors(builder._delimiterProcessors)
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
}
