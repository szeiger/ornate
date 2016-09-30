package com.novocode.ornate

import scala.collection.mutable
import java.util.regex.Pattern
import better.files._

/** Pattern matcher for gitignore-style patterns */
class FileMatcher(val patterns: Vector[String]) {
  case class Entry(pattern: String, compiled: Pattern, negate: Boolean, dirOnly: Boolean)

  def this(gitignore: String) =
    this(gitignore.split("\n").iterator.map(_.replace("\r", "")).filter { s =>
      val t = s.trim
      t.nonEmpty && !t.startsWith("#")
    }.toVector)

  val entries: Vector[Entry] = patterns.map(build)

  private def build(pattern: String): Entry = {
    var p = pattern
    while(p.endsWith(" ") && !p.endsWith("\\ ")) p = p.dropRight(1)
    val negate = p.startsWith("!")
    if(negate) p = p.substring(1)
    val dirOnly = p.endsWith("/")
    if(dirOnly) p = p.dropRight(1)
    if(p.startsWith("**/")) p = p.substring(3)
    var i = 0
    val b = new mutable.StringBuilder
    val len = p.length
    if(!p.startsWith("/")) b.append(".*/")
    def appendLiteral(c: Char): Unit =
      if(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') b.append(c)
      else b.append(Pattern.quote(String.valueOf(c)))
    while(i < len) {
      p.charAt(i) match {
        case '/' =>
          if(i == 0) b.append("^/") else b.append('/')
        case '*' if i+1 < len && p.charAt(i+1) == '*' =>
          if((i != 0 && p.charAt(i-1) != '/') || (i+2 != len && p.charAt(i+2) != '/')) {
            b.append("[^/]*") // illegal `**`, treat as two `*` (same as one `*`)
            i += 1
          } else {
            b.append("([^/]*/)*")
            i += 2
          }
        case '*' =>
          b.append("[^/]*")
        case '\\' =>
          if(i+1 < len) appendLiteral(p.charAt(i+1))
          i += 1
        case c =>
          appendLiteral(c)
      }
      i += 1
    }
    if(!b.endsWith("/")) b.append('/')
    Entry(pattern, Pattern.compile(b.toString), negate, dirOnly)
  }

  private def matchesThis(absPath: String, isDir: Boolean): Boolean = {
    var matched = false
    for(e <- entries)
      if(e.negate == matched && (isDir || !e.dirOnly) && e.compiled.matcher(absPath + '/').matches)
        matched = !matched
    matched
  }

  def filterAny[T](dir: T)(getName: T => String)(isDir: T => Boolean)(getChildren: T => Vector[T]): Vector[T] = {
    def filter(f: T, basePath: String): Vector[T] = {
      val path = basePath + '/' + getName(f)
      val d = isDir(f)
      if(!matchesThis(path, d)) {
        if(d) f +: getChildren(f).flatMap(ch => filter(ch, path))
        else Vector(f)
      } else Vector.empty
    }
    getChildren(dir).flatMap(ch => filter(ch, ""))
  }

  def filter(dir: File): Vector[File] = filterAny(dir)(_.name)(_.isDirectory)(_.children.toVector)

  def matchesPath(absPath: String, isDir: Boolean = false): Boolean = {
    val elems = absPath.split('/').iterator.filter(_.nonEmpty)
    var path = ""
    while(elems.hasNext) {
      path = path + '/' + elems.next
      if(matchesThis(path, isDir || elems.hasNext)) return true
    }
    false
  }
}
