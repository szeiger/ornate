package com.novocode.ornate

import javax.script.ScriptContext

import com.novocode.ornate.js.{JSMap, NashornSupport}
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
      case "test3:/index.js" => Some(
        """exports.f = function() {
          |  console.log('window = '+window); };
          |  console.log('foo = '+window.getElementById('foo')); };
          |};
        """.stripMargin)
      case "test4:/index.js" => Some(
        """"use strict";
          |exports = 42;
        """.stripMargin)
      case "test5:/index.js" => Some(
        """var a = require('test6');
          |function b() {};
          |b.prototype = Object.create(a.prototype, {});
        """.stripMargin)
      case "test6:/index.js" => Some(
        """module.exports = a;
          |function a() {};
          |a.prototype = Object.create(Object.prototype, {});
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

  @Test def testHighlightJS2: Unit = {
    val nashorn = createNashorn
    val hl = nashorn.mainModule.require("highlight.js/lib/highlight.js")
    nashorn.call[Unit](hl, "registerLanguage", "scala", nashorn.mainModule.require(s"highlight.js/lib/languages/scala.js"))
    println(nashorn.call[JSMap](hl, "highlight", "scala", "\"<foo>\"").apply[String]("value"))
  }

  /*
  @Test def testMermaid: Unit = {
    val nashorn = createNashorn
    nashorn.engineBindings.put("console", nashorn.mainModule.require("console"))
    nashorn.engine.eval("global.SVGElement = function() { throw 'not implemented'; };")
    nashorn.engineBindings.put("mermaid", nashorn.mainModule.require("mermaid"))
    nashorn.engine.eval(
      """var mermaidAPI = mermaid.mermaidAPI;
        |mermaidAPI.initialize({ startOnLoad: false });
        |var graphDefinition = 'graph TB\na-->b';
        |console.log('Rendering graph...');
        |var cb = function(html) { console.log(html); }
        |var res = mermaidAPI.render('id1',graphDefinition,cb);
        |console.log('Result: '+res);
        |console.log('Finished.');
      """.stripMargin)
  }

  @Test def testDomino: Unit = {
    val nashorn = createNashorn
    nashorn.engine.eval(
      """(function() {
        |  var domino = require('domino');
        |  global.window = domino.createWindow('<html></html>');
        |  global.document = window.document;
        |})();
      """.stripMargin)
    val test3 = nashorn.mainModule.require("test3")
    nashorn.call[Unit](test3, "f")
  }

  @Test def testStrict: Unit = {
    val nashorn = createNashorn
    nashorn.mainModule.require("test4")
  }

  @Test def testCrossModuleBindings: Unit = {
    val nashorn = createNashorn
    nashorn.mainModule.require("test5")
  }
  */

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
