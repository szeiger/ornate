package com.novocode.ornate

import com.novocode.ornate.js.CSSO
import org.junit.Test
import org.junit.Assert._

class MinifyTest {
  @Test def testCSSO: Unit = {
    val min = CSSO.minify(".test { color: #ff0000; }")
    assertEquals(".test{color:red}", min)
  }

  @Test def testClosure: Unit = {
    val min = Util.closureMinimize("var b = function () {};")
    assertEquals("var b=function(){};", min)
  }

  @Test def testHtmlCompressor: Unit = {
    val min = Util.htmlCompressorMinimize(
      """<html><head>
        |  <script type="text/javscript">
        |    var b = function () {};
        |  </script>
        |  <style>
        |    .test { color: #ff0000; }
        |  </style>
        |</head><body>
        |  <p class="test">Text</p>
        |</body></html>
      """.stripMargin, minimizeCss = true, minimizeJs = true)
    assertEquals("<html><head> <script type=\"text/javscript\">var b=function(){};</script> <style>.test{color:red}</style> </head><body> <p class=test>Text</p> </body></html>", min)
  }
}
