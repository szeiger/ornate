package com.novocode.mdoc

import scala.annotation.tailrec
import java.net.URI
import java.util.Locale

object Util {
  def createIdentifier(text: String) = {
    val s = text.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "-").filter {
      case '_' | '-' | '.' => true
      case c if Character.isDigit(c) || Character.isAlphabetic(c) => true
      case _ => false
    }.dropWhile(c => !Character.isAlphabetic(c))
    if(s.nonEmpty) s else "section"
  }

  /** Create a relative link from one page to another. Both URIs must be absolute "doc:" URIs
    * without "." or ".." path segments, ending in file names (i.e. no tailing "/"). */
  def relativeDocURI(from: URI, to: URI, suffix: String = ""): URI = {
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
      new URI(null, null, relPath + suffix, to.getQuery, to.getFragment)
    }
  }
}
