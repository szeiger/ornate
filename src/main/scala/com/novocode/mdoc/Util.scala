package com.novocode.mdoc

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import java.net.{URLEncoder, URI}
import java.util.Locale
import better.files._

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

  def sourceFileURI(baseDir: File, file: File): URI = {
    val segments = baseDir.relativize(file).iterator().asScala.toVector.map(s => URLEncoder.encode(s.toString, "UTF-8"))
    siteRootURI.resolve(segments.mkString("/", "/", ""))
  }
}
