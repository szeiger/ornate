package com.novocode.mdoc

import java.net.URI

import org.junit.Test
import org.junit.Assert._

class UtilTest {

  @Test def testRelativeDocURI: Unit = {
    def check(f: String, t: String, e: String): Unit =
      assertEquals(e, Util.relativeSiteURI(new URI(f), new URI(t)).toString)
    check("site:/a#f", "site:/b", "b")
    check("site:/a", "site:/b#f", "b#f")
    check("site:/a/b/c", "site:/d/e/f", "../../d/e/f")
    check("site:/a/b", "site:/a/b", "b")
    check("site:/a/b#f", "site:/a/b#f", "#f")
    check("site:/a/b", "site:/a/c", "c")
    check("site:/a/b#f", "site:/a/b#f", "#f")
    check("site:/a/b", "site:/a/c", "c")
  }
}
