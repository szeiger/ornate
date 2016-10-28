package com.novocode.ornate

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

import com.google.javascript.jscomp.CompilationLevel
import com.googlecode.htmlcompressor.compressor.{Compressor, ClosureJavaScriptCompressor, HtmlCompressor}
import com.novocode.ornate.js.CSSO

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import java.net.{URL, URLEncoder, URI}
import java.util.Locale
import better.files._

import scala.io.Codec

object Util {
  val siteRootURI = new URI("site", null, "/", null)
  val themeRootURI = new URI("theme", null, "/", null)

  def createIdentifier(text: String) = {
    val s = text.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "-").filter {
      case '_' | '-' | '.' => true
      case c if Character.isDigit(c) || Character.isAlphabetic(c) => true
      case _ => false
    }.dropWhile(c => !Character.isAlphabetic(c))
    if(s.nonEmpty) s else "section"
  }

  def replaceSuffix(u: URI, suffix: String, newSuffix: String): URI =
    if(suffix.isEmpty && newSuffix.isEmpty) u
    else {
      val p = u.getPath
      val p2 =
        if(p.toLowerCase(Locale.ENGLISH).endsWith(suffix.toLowerCase(Locale.ENGLISH)))
          p.substring(0, p.length-suffix.length)
        else p
      new URI(u.getScheme, u.getAuthority, p2 + newSuffix, u.getQuery, u.getFragment)
    }

  /** Create a relative link from one page to another. Both URIs must be absolute "site:" URIs
    * without "." or ".." path segments, ending in file names (i.e. no tailing "/"). */
  def relativeSiteURI(from: URI, to: URI): URI = {
    @tailrec def differentTail(f: List[String], t: List[String]): (List[String], List[String]) =
      if(f.isEmpty || t.isEmpty || f.head != t.head) (f, t)
      else differentTail(f.tail, t.tail)
    val fromPath = from.getPath.split('/').toList
    val fromDir = fromPath.init
    val fromPage = fromPath.last
    val toPath = to.getPath.split('/').toList
    val toDir = toPath.init
    val toPage = toPath.last
    val (fromTail, toTail) = differentTail(fromDir, toDir)
    if(fromTail.isEmpty && toTail.isEmpty && fromPage == toPage && (to.getFragment ne null))
      new URI(null, null, null, to.getQuery, to.getFragment)
    else {
      val relPath = ((fromTail.map(_ => "..") ::: toTail) :+ toPage).mkString("/")
      new URI(null, null, relPath, to.getQuery, to.getFragment)
    }
  }

  def rewriteIndexPageLink(rel: URI, indexPage: Option[String]): URI = indexPage match {
    case Some(s) =>
      val p = rel.getPath
      if(p == s)
        new URI(rel.getScheme, rel.getAuthority, "./", rel.getQuery, rel.getFragment)
      else if(p.endsWith("/"+s))
        new URI(rel.getScheme, rel.getAuthority, p.substring(0, p.length-s.length), rel.getQuery, rel.getFragment)
      else rel
    case None => rel
  }

  def sourceFileURI(baseDir: File, file: File): URI = {
    val segments = baseDir.relativize(file).iterator().asScala.toVector.map(s => URLEncoder.encode(s.toString, "UTF-8"))
    siteRootURI.resolve(segments.mkString("/", "/", ""))
  }

  def sha1(data: Array[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-1")
    md.update(data)
    md.digest()
    String.format("%040x", new BigInteger(1, md.digest()))
  }

  def copyToFile(source: URL, target: File): Unit = {
    target.parent.createDirectories()
    val in = source.openStream()
    try {
      val out = target.newOutputStream
      try in.pipeTo(out) finally out.close
    } finally in.close
  }

  def copyToFileWithTextTransform(source: URL, target: File)(f: String => String)(implicit codec: Codec): Unit = {
    target.parent.createDirectories()
    val in = source.openStream()
    val s = try {
      val out = new ByteArrayOutputStream()
      in.pipeTo(out)
      out.toString(codec.name)
    } finally in.close
    val s2 = f(s)
    target.write(s2)
  }

  def closureMinimize(source: String, name: String = "<eval>"): String = {
    import com.google.javascript.jscomp._
    val compiler = new Compiler
    val options = new CompilerOptions
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options)
    val extern = AbstractCommandLineRunner.getBuiltinExterns(CompilerOptions.Environment.BROWSER)
    val input = SourceFile.fromCode(name, source)
    compiler.compile(extern, List(input).asJava, options)
    compiler.toSource
  }

  def htmlCompressorMinimize(source: String, minimizeCss: Boolean, minimizeJs: Boolean): String = {
    val c = new HtmlCompressor
    c.setRemoveQuotes(true)
    if(minimizeCss) {
      c.setCompressCss(true)
      c.setCssCompressor(new Compressor {
        def compress(source: String): String = CSSO.minify(source)
      })
    }
    if(minimizeJs) {
      c.setCompressJavaScript(true)
      c.setJavaScriptCompressor(new Compressor {
        def compress(source: String): String = closureMinimize(source, "<inline>")
      })
    }
    c.compress(source)
  }
}
