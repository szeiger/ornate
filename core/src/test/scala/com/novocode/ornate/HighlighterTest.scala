package com.novocode.ornate

import java.net.URI

import better.files._
import com.novocode.ornate.config.Global
import com.novocode.ornate.highlight.HighlightJSExtension
import com.novocode.ornate.highlight.HighlightJSHighlighter
import com.novocode.ornate.highlight.HighlightResult
import org.commonmark.node.FencedCodeBlock
import org.junit.Test
import org.junit.Assert._
import play.twirl.api.HtmlFormat

class HighlighterTest {
  val global = new Global(file"../doc", None)
  val p1 = PageParser.parseWithFrontMatter(None, None, global.referenceConfig, new URI("site:/p1"), "", "p1 content")
  val p2 = PageParser.parseWithFrontMatter(None, None, global.referenceConfig, new URI("site:/p2"), "",
    """---
      |extension.highlightjs.fenced: rust
      |---
      |p2 content
    """.stripMargin)
  val p3 = PageParser.parseWithFrontMatter(None, None, global.referenceConfig, new URI("site:/p3"), "",
    """---
      |extension.highlightjs.fenced: [json, scala, rust]
      |---
      |p3 content
    """.stripMargin)
  val p4 = PageParser.parseWithFrontMatter(None, None, global.referenceConfig, new URI("site:/p4"), "",
    """---
      |extension.highlightjs.fenced: null
      |---
      |p4 content
    """.stripMargin)

  @Test def testHighlightJS: Unit = {
    val hlext = global.userConfig.getExtensions(Seq("highlightjs")).ornate.head.asInstanceOf[HighlightJSExtension]
    val hl = hlext.pageProcessors(null).head.asInstanceOf[HighlightJSHighlighter]
    def check(p: Page, src: String, lang: Option[String], exp: String, expLang: Option[String]): Unit = {
      val hlr = hl.highlightTextAsHTML(src, lang, new FencedCodeBlock, p)
      assertEquals(HtmlFormat.raw(exp).toString, hlr.html)
      assertEquals(expLang, hlr.language)
    }

    check(p1,
      """val x = "<foo>"""", Some("scala"),
      """<span class="hljs-keyword">val</span> x = <span class="hljs-string">"&lt;foo&gt;"</span>""", Some("scala"))
    check(p2,
      """val x = "<foo>"""", None,
      """val x = <span class="hljs-string">"&lt;foo&gt;"</span>""", Some("rust"))
    check(p3,
      """val x = "<foo>"""", Some("scala"),
      """<span class="hljs-keyword">val</span> x = <span class="hljs-string">"&lt;foo&gt;"</span>""", Some("scala"))
    check(p3,
      """val x = "<foo>"""", None,
      """<span class="hljs-keyword">val</span> x = <span class="hljs-string">"&lt;foo&gt;"</span>""", Some("scala"))
    check(p4,
      """val x = "<foo>"""", None,
      """val x = &quot;&lt;foo&gt;&quot;""", None)
    check(p2,
      """val x = "<foo>"""", Some("text"),
      """val x = &quot;&lt;foo&gt;&quot;""", None)
    check(p1,
      """val x = "<foo>"""", None,
      """<span class="hljs-keyword">val</span> x = <span class="hljs-string">"&lt;foo&gt;"</span>""", Some("scala"))
    check(p1,
      """val x = "<foo>"""", Some("foo"),
      """val x = &quot;&lt;foo&gt;&quot;""", None)
  }

  @Test def testNoHighlight: Unit = {
    val exp = """val x = &quot;&lt;foo&gt;&quot;"""
    assertEquals(HighlightResult(HtmlFormat.raw(exp).toString, None),
      HighlightResult.simple("val x = \"<foo>\""))
  }
}
