package com.novocode.mdoc

import javax.script.ScriptContext

import com.novocode.mdoc.js.{JSMap, NashornSupport}
import org.junit.Assert._
import org.junit.Test

class NashornTest {
  def createNashorn: NashornSupport = new NashornSupport with Logging {
    override def loadAsset(webjar: String, exactPath: String): Option[String] = s"$webjar:/$exactPath" match {
      case "test1:/index.js" => Some(
        """exports.f = function(v) { console.log('from test1.f: '+v); console.trace(); };""")
      case "test2:/index.js" => Some(
        """exports.f = function() { console.log('global.foo = '+global.foo); };
          |exports.g = function() { console.log('foo = '+foo); };
        """.stripMargin)
      case _ => super.loadAsset(webjar, exactPath)
    }
  }

  @Test def testRequireHighlightJS: Unit = {
    val nashorn = createNashorn
    val hl = nashorn.mainModule.require("highlight.js")
    println(nashorn.call[Vector[String]](hl, "listLanguages"))
    println(nashorn.call[JSMap](hl, "highlight", "scala", "\"<foo>\"").apply[String]("value"))
  }

  /*@Test def testRequireMermaid: Unit = {
    val nashorn = createNashorn
    val mermaid = nashorn.mainModule.require("mermaid")
  }*/

  @Test def testConsole: Unit = {
    val nashorn = createNashorn
    // Require manually:
    nashorn.engine.eval("require('console').log('Logging values %d and %s', 42, 'foo');")
    nashorn.engine.eval("require('console').debug('debug log');")
    // Put into global scope, available to all modules:
    nashorn.engineBindings.put("console", nashorn.mainModule.require("console"))
    val test1 = nashorn.mainModule.require("test1")
    nashorn.call[Unit](test1, "f", "foo")
    nashorn.engineBindings.put("foo", 42)
    val test2 = nashorn.mainModule.require("test2")
    nashorn.call[Unit](test2, "f")
    nashorn.call[Unit](test2, "g")
  }
}
