package com.novocode.ornate

import java.io.{BufferedReader, FileNotFoundException, InputStreamReader}
import java.net.{URI, URLDecoder}
import java.util.regex.Pattern

import URIExtensionMethods._
import com.novocode.ornate.commonmark.Attributed
import com.novocode.ornate.commonmark.AttributedFencedCodeBlock
import com.novocode.ornate.commonmark.NodeExtensionMethods
import NodeExtensionMethods._
import com.novocode.ornate.config.{ConfiguredObject, Global}
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.typesafe.config.ConfigValueType
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock

import scala.collection.mutable.ArrayBuffer
import scala.io.Codec

/** Include code snippets from external files in fenced code blocks. */
class IncludeCodeExtension(co: ConfiguredObject) extends Extension with Logging {
  case class SourceLink(dir: URI, uri: URI)
  case class Parsed(removePatterns: Map[String, String], sourceLinks: Vector[SourceLink])
  case class Snippet(content: String, lines: Option[Seq[(Int, Int)]])

  val parse = co.memoizeParsed { c =>
    val removePatterns = c.getConfigMapOr("remove").iterator.collect {
      case (k, v) if v.valueType != ConfigValueType.NULL => (k, v.unwrapped.toString)
    }.toMap
    val base = co.global.userConfig.sourceDir.uri
    val sourceLinks = c.getConfigMapOr("sourceLinks").iterator.collect {
      case (k, v) if v.valueType != ConfigValueType.NULL => SourceLink(base.resolve(k), new URI(v.unwrapped.toString))
    }.toVector
    Parsed(removePatterns, sourceLinks)
  }

  override def pageProcessors(site: Site) = Seq(new PageProcessor {
    def runAt: Phase = Phase.Include
    def apply(p: Page): Unit = p.doc.accept(new IncludeCodeVisitor(p, parse(p.config)))
  })

  class IncludeCodeVisitor(p: Page, parsed: Parsed) extends AbstractVisitor {
    override def visit(n: FencedCodeBlock): Unit = {
      val attr = n.asInstanceOf[AttributedFencedCodeBlock]
      attr.defAttrs.get("src").foreach { src =>
        try {
          p.sourceFileURI match {
            case Some(baseURI) =>
              logger.debug(s"Including snippet $src on page ${p.uri}")
              val snippetURI = baseURI.resolve(src)
              getSnippet(snippetURI, parsed) match {
                case Some(snippet) =>
                  n.setLiteral(snippet.content)
                  if(!attr.defAttrs.contains("sourceLinkURI")) {
                    getSourceLink(snippetURI, parsed, snippet.lines).foreach { link =>
                      attr.defAttrs.put("sourceLinkURI", link.toString)
                      logger.debug(s"Setting sourceLinkURI for snippet $src to $link")
                    }
                  }
                case None =>
                  logger.error(s"No content found for snippet $src on page ${p.uri}")
              }
            case None =>
              logger.error(s"Cannot include snippet $src on synthetic page ${p.uri}")
          }
        } catch {
          case ex: FileNotFoundException => logger.error(s"Page ${p.uri}: File for snippet $src not found")
          case ex: Exception => logger.error(s"Page ${p.uri}: Error including snippet $src", ex)
        }
      }
    }

    def getSourceLink(snippetURI: URI, parsed: Parsed, lines: Option[Seq[(Int, Int)]]): Option[URI] = {
      val fileURI = snippetURI.copy(query = null, fragment = null)
      val o = parsed.sourceLinks.iterator.map(sl => (sl, sl.dir.relativize(fileURI))).find(t => !t._2.isAbsolute)
      o.map { case (sl, rel) =>
        val u = sl.uri.resolve(rel)
        if(sl.uri.getFragment == "ghLines" && lines.map(_.nonEmpty).getOrElse(false)) {
          val first = lines.get.head._1
          val last = lines.get.last._2
          u.copy(fragment = s"L$first-L$last")
        } else u
      }
    }

    def getSnippet(snippetURI: URI, parsed: Parsed): Option[Snippet] = {
      val snippetPath = snippetURI.getPath
      val remove: Option[Pattern] = parsed.removePatterns.find { case (ext, re) =>
        snippetPath.endsWith(s".$ext")
      }.map { case (ext, re) => Pattern.compile(re) }
      val fileURI = snippetURI.copy(fragment = null)
      val fragment = snippetURI.getFragment
      val in = fileURI.toURL.openStream()
      val lines = try {
        val bin = new BufferedReader(new InputStreamReader(in, Codec.UTF8.decoder))
        Iterator.continually(bin.readLine()).takeWhile(_ ne null).toVector
      } finally in.close
      val trimmed = lines.map { s =>
        val last = s.lastIndexWhere(c => c != ' ' && c != '\t')
        if(last == -1) "" else s.substring(0, last+1)
      }
      if(fragment eq null) {
        val first = trimmed.indexWhere(_.nonEmpty)
        val last = trimmed.lastIndexWhere(_.nonEmpty)
        val content = trimmed.slice(first, last+1)
        Some(Snippet(content.mkString("\n"), None))
      } else {
        val suffix = "#" + fragment
        logger.debug("Building snippet for fragement "+suffix)
        var found = false
        val buf = new ArrayBuffer[String]
        var linenos = new ArrayBuffer[(Int, Int)]
        var blockBuf: ArrayBuffer[String] = null
        var inBlock = false
        var startOffset, line, startLine = 0
        def startBlock(offset: Int): Unit = {
          found = true
          inBlock = true
          blockBuf = new ArrayBuffer[String]
          startLine = line
          startOffset = offset
        }
        def endBlock(endOffset: Int): Unit = {
          inBlock = false
          var offset = math.min(startOffset, endOffset)
          blockBuf.foreach { s =>
            val soff = s.indexWhere(c => c != ' ' && c != '\t')
            if(soff >= 0) offset = math.min(offset, soff)
          }
          blockBuf.foreach(s => buf += s.substring(math.min(offset, s.length)))
          if(line-startLine > 2)
            linenos += (((startLine+1), (line-1)))
        }
        trimmed.iterator.zipWithIndex.foreach { case (s, idx) =>
          line = idx+1
          if(s.endsWith(suffix)) {
            val offset = s.length - suffix.length
            if(inBlock) endBlock(offset)
            else startBlock(offset)
          } else if(inBlock && !remove.exists(p => p.matcher(s).matches())) blockBuf += s
        }
        if(inBlock) endBlock(0)
        if(found) Some(Snippet(buf.mkString("\n"), Some(linenos))) else None
      }
    }
  }
}
