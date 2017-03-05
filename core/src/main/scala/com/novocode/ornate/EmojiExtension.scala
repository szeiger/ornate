package com.novocode.ornate

import scala.collection.mutable.ArrayBuffer

import java.net.URI

import com.novocode.ornate.commonmark.Attributed
import com.novocode.ornate.config.ConfiguredObject
import com.novocode.ornate.js.WebJarSupport
import com.typesafe.config.Config
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.PostProcessor
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json

/** Replace emoji names with images. */
class EmojiExtension(co: ConfiguredObject) extends Extension {
  val ext = Seq(new EmojiParserExtension(co))
  override def parserExtensions(pageConfig: Config) = ext
}

class EmojiParserExtension(co: ConfiguredObject) extends Parser.ParserExtension with WebJarSupport with Logging {
  import EmojiParserExtension._
  val format = co.config.getString("global.format")
  val formatSuffix = format.replaceAll("_.*$", "")

  def extend(builder: Parser.Builder): Unit = builder.postProcessor(EmojiDelimiterProcessor)

  case class EmojiData(names: Seq[String], unicode: Seq[String])

  val emojiOneData = {
    val raw = Json.parse(loadAsset("emojione", "emoji.json").get).asInstanceOf[JsObject].fieldSet
    raw.iterator.flatMap { case (name, v) =>
      val data = v.asInstanceOf[JsObject].value
      val aliases = data("aliases").asInstanceOf[JsArray].value.map { v =>
        val s = v.asInstanceOf[JsString].value
        assert(s.startsWith(":") && s.endsWith(":"))
        s.substring(1, s.length-1)
      }
      val names = name +: aliases
      val unicode = data("unicode").asInstanceOf[JsString].value
      val unicodeAlt = data.get("unicode_alternates").map(_.asInstanceOf[JsString].value) match {
        case Some("") => None
        case o => o
      }
      val e = EmojiData(names, unicode +: unicodeAlt.toVector)
      e.names.map(n => (n, e))
    }.toMap
  }

  def shortnameToUnicode(n: String): Option[String] = emojiOneData.get(n).map { e =>
    e.unicode.last.split('-').flatMap(s => Character.toChars(Integer.parseInt(s, 16))).mkString
  }

  def shortnameToImage(n: String): Option[URI] = emojiOneData.get(n).flatMap { e =>
    val pathOpt = e.unicode.reverseIterator.map { n =>
      val path = s"assets/$format/$n.$formatSuffix"
      if(WebJarSupport.getFullPathExact("emojione", path).isEmpty) null else path
    }.find(_ ne null)
    pathOpt.map { path => new URI(s"webjar:/emojione/$path") }
  }

  def findEmojiCandidates(s: String): IndexedSeq[(Int, Int)] = {
    val len = s.length
    var i, i0 = 0
    def findEnd: Int = {
      var j = i+1
      while(j < len) {
        var c = s.charAt(j)
        if(c == ':') {
          if(j != i+1 /*&& (j == len-1 || Character.isWhitespace(s.charAt(j+1)))*/) return j
          else return -1
        }
        else if(c != '_' && !Character.isUnicodeIdentifierPart(c) && !Character.isDigit(c)) return -1
        j += 1
      }
      -1
    }
    var emojiStart = -1
    var wasSpace = true
    var buf: ArrayBuffer[(Int, Int)] = null
    while(i < len) {
      var c = s.charAt(i)
      if(Character.isWhitespace(c)) wasSpace = true
      else {
        if(c == ':' && wasSpace) {
          val end = findEnd
          if(end != -1) {
            if(buf eq null) buf = new ArrayBuffer
            buf += ((i, end))
            i = end
          }
        }
        wasSpace = false
      }
      i += 1
    }
    if(buf eq null) Vector.empty else buf
  }

  def replaceEmojis(s: String): Option[IndexedSeq[Replacement]] = {
    val cand = findEmojiCandidates(s)
    if(cand.isEmpty) None else {
      var found = false
      val confirmed = cand.flatMap { case (start, end) =>
        val short = s.substring(start+1, end)
        val o = (format match {
          case "unicode" => shortnameToUnicode(short).map(u => EmojiUnicode(short, u))
          case _ => shortnameToUnicode(short).flatMap(u => shortnameToImage(short).map(i => EmojiImage(short, u, i)))
        }).map(data => (start, end, data))
        if(o.isDefined) found = true
        o
      }
      if(!found) None else {
        val buf = new ArrayBuffer[Replacement]
        var copied = 0
        val len = s.length
        confirmed.foreach { case (start, end, data) =>
          if(start > copied) buf += PlainText(s.substring(copied, start))
          copied = end+1
          buf += data
        }
        if(len > copied) buf += PlainText(s.substring(copied, len))
        Some(buf)
      }
    }
  }

  object EmojiDelimiterProcessor extends PostProcessor {
    def process(node: Node): Node = {
      node.accept(new AbstractVisitor {
        override def visit(n: Text): Unit = {
          replaceEmojis(n.getLiteral) match {
            case Some(repl) =>
              repl.foreach {
                case PlainText(s) =>
                  n.insertBefore(new Text(s))
                case EmojiUnicode(name, unicode) =>
                  n.insertBefore(new Emoji(name, unicode, null))
                case EmojiImage(name, unicode, uri) =>
                  n.insertBefore(new Emoji(name, unicode, uri))
              }
              n.unlink()
            case None =>
              super.visit(n)
          }
        }
      })
      node
    }
  }
}

object EmojiParserExtension {
  sealed trait Replacement
  case class PlainText(text: String) extends Replacement
  case class EmojiUnicode(name: String, unicode: String) extends Replacement
  case class EmojiImage(name: String, unicode: String, uri: URI) extends Replacement
}

class Emoji(var name: String, var unicode: String, var uri: URI) extends CustomNode
