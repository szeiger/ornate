package com.novocode.mdoc

import java.net.URI

import org.junit.Test
import org.junit.Assert._

class UtilTest {

  @Test def testRelativeDocURI: Unit = {
    def check(f: String, t: String, e: String, suffix: String = ""): Unit =
      assertEquals(e, Util.relativeDocURI(new URI(f), new URI(t), suffix).toString)
    check("doc:/a#f", "doc:/b", "b")
    check("doc:/a", "doc:/b#f", "b#f")
    check("doc:/a/b/c", "doc:/d/e/f", "../../d/e/f")
    check("doc:/a/b", "doc:/a/b", "b")
    check("doc:/a/b#f", "doc:/a/b#f", "#f")
    check("doc:/a/b", "doc:/a/c", "c")
    check("doc:/a/b#f", "doc:/a/b#f", "#f", ".html")
    check("doc:/a/b", "doc:/a/c", "c.html", ".html")
  }
}
