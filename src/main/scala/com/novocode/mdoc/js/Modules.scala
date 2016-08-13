/*
 * This code is based on https://github.com/coveo/nashorn-commonjs-modules which was published under the
 * following license:
 *
 * The MIT License (MIT)
 * Copyright (c) 2016 Coveo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.novocode.mdoc.js

import com.novocode.mdoc.Logging

import scala.collection.mutable
import scala.collection.JavaConverters._

import java.util.{ArrayList => JArrayList, HashMap => JHashMap, Map => JMap}
import javax.script.{Bindings, ScriptContext, ScriptException, SimpleBindings}
import jdk.nashorn.api.scripting.{ScriptUtils, NashornScriptEngine, ScriptObjectMirror}
import jdk.nashorn.internal.runtime.{ScriptObject, ECMAException}

class Modules(engine: NashornScriptEngine) extends Logging {
  import Modules._
  private val cache = new mutable.HashMap[Vector[String], Module]

  private var _main: Module =
    new Module(Vector("main"), engine.getBindings(ScriptContext.ENGINE_SCOPE), None).initialized
  lazy val main: Module = _main

  def resolve(path: Vector[String]): Option[String] = None
  def resolveCore(name: String): Option[AnyRef] = None

  class Module(val modulePath: Vector[String], private val global: Bindings, parent: Option[Module]) extends SimpleBindings with RequireFunction { self =>
    private val module = new SimpleBindings
    private var exports: AnyRef = engine.eval("new Object()").asInstanceOf[ScriptObjectMirror]
    private[this] val children = new JArrayList[Bindings]
    put("main", (if(_main eq null) this else _main).module)
    global.put("require", this)
    global.put("global", global)
    global.put("module", module)
    global.put("exports", exports)
    module.put("exports", exports)
    module.put("children", children)
    module.put("filename", modulePath.lastOption.getOrElse(null))
    module.put("id", module.get("filename"))
    module.put("loaded", false)
    module.put("parent", parent.map(_.module).getOrElse(null))

    engine.eval( // Patch Object.create to unwrap ScriptObjectMirror to ScriptObject
      s"""(function() {
      var su = Java.type('${classOf[Modules].getName}');
      var c = Object.create;
      Object.create = function(p, v) { return c(su.unwrap(p), v); };
    })();""", global)

    private[Modules] def initialized: this.type = { module.put("loaded", true); this }

    def require(module: String): AnyRef = {
      val exports = try {
        logger.debug(s"require('$module')")
        loadCoreModule(module).orElse {
          if(module.startsWith("/")  || module.startsWith("../")  || module.startsWith("./")) {
            val abs = resolvePath(modulePath.init, module.split('/'))
            abs.flatMap(loadAsFile _).orElse(abs.flatMap(loadAsDirectory _))
          } else None
        }.orElse(loadNodeModules(module.split('/'), modulePath.init)).map { f =>
          children.add(f.module)
          if(logger.isDebugEnabled) logger.debug(s"require('$module'): Exported " + (f.exports match {
            case s: ScriptObjectMirror => s"symbols: "+s.keySet().asScala.mkString(", ")
            case null                  => s"null"
            case o                     => s"object (${o.getClass.getName}): $o"
          }))
          f.exports
        }
      } catch { case ex: Exception => fail(module, s"Error loading module: $module", ex) }
      exports.getOrElse(fail(module, s"Module not found: $module"))
    }

    private[this] def fail(module: String, msg: String, parent: Throwable = null): Nothing = {
      logger.warn(s"require('$module'): Error: $msg", parent)
      val error = engine.eval("Error").asInstanceOf[ScriptObjectMirror].newObject(msg).asInstanceOf[Bindings]
      error.put("code", "MODULE_NOT_FOUND");
      throw new ECMAException(error, null)
    }

    private[this] def loadAsFile(path: Vector[String]): Option[Module] =
      loadJSModule(path).orElse {
        val (init, last) = (path.init, path.last)
        loadJSModule(init :+ (last + ".js")).orElse(loadJSONModule(init :+ (last + ".json")))
      }

    private[this] def loadAsDirectory(path: Vector[String]): Option[Module] =
      resolve(path :+ "package.json").flatMap { s =>
        Option(parseJson(s).get("main").asInstanceOf[String]).flatMap(m => resolvePath(path, m.split('/')).flatMap(loadAsFile _))
      }.orElse(loadJSModule(path :+ "index.js")).orElse(loadJSONModule(path :+ "index.json"))

    private[this] def loadNodeModules(path: Iterable[String], start: Vector[String]): Option[Module] =
      start.inits.filterNot(_.endsWith("node_modules")).map(_ :+ "node_modules").map { d =>
        loadAsFile(d ++ path).orElse(loadAsDirectory(d ++ path))
      }.find(_.isDefined).flatten

    private[this] def loadJSModule(path: Vector[String]): Option[Module] =
      cached(path)(p => resolve(p).map { code =>
        val m = new Module(p, new SimpleBindings(new JHashMap(engine.getBindings(ScriptContext.ENGINE_SCOPE))), Some(this))
        engine.eval(code, m.global)
        m.exports = m.module.get("exports")
        m.initialized
      })

    private[this] def loadJSONModule(path: Vector[String]) = cached(path)(p => resolve(p).map { code =>
      val m = new Module(p, new SimpleBindings, Some(this))
      m.exports = parseJson(code)
      m.initialized
    })

    private[this] def loadCoreModule(name: String) = cached(Vector(name))(p => resolveCore(name).map { exp =>
      val m = new Module(p, new SimpleBindings, Some(this))
      m.exports = exp
      m.initialized
    })
  }

  private def cached(path: Vector[String])(f: Vector[String] => Option[Module]) = cache.get(path).orElse {
    val mo = f(path)
    mo.foreach(cache.put(path, _))
    mo
  }

  private def parseJson(json: String) =
    engine.eval("JSON").asInstanceOf[ScriptObjectMirror].callMember("parse", json).asInstanceOf[ScriptObjectMirror]
}

object Modules {
  def unwrap(o: ScriptObjectMirror): AnyRef = if(o eq null) null else {
    val f = classOf[ScriptObjectMirror].getDeclaredField("sobj")
    f.setAccessible(true)
    f.get(o)
  }

  private def resolvePath(base: Vector[String], rel: Iterable[String]): Option[Vector[String]] = rel.foldLeft(Option(base)) {
    case (z, ("" | ".")) => z
    case (z, "..") => z.flatMap(zz => if(zz.length > 0) Some(zz.dropRight(1)) else None)
    case (z, n) => z.map(_ :+ n)
  }
}

@FunctionalInterface trait RequireFunction { def require(module: String): AnyRef }
