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
  private val cache = new mutable.HashMap[String, Option[Module]]

  private var _main: Module =
    new Module(Folder(None, "/"), "<main>", engine.getBindings(ScriptContext.ENGINE_SCOPE), None).initialized
  lazy val main: Module = _main

  def resolve(path: String): Option[String] = None
  def resolveNative(path: String): Option[AnyRef] = None

  class Module(folder: Folder, filename: String, protected val global: Bindings, parent: Option[Module]) extends SimpleBindings with RequireFunction { self =>
    protected val module = new SimpleBindings
    protected var exports: AnyRef = engine.eval("new Object()").asInstanceOf[ScriptObjectMirror]
    private[this] val children = new JArrayList[Bindings]
    put("main", (if(_main eq null) this else _main).module)
    global.put("require", this)
    global.put("global", global)
    global.put("module", module)
    global.put("exports", exports)
    module.put("exports", exports)
    module.put("children", children)
    module.put("filename", filename)
    module.put("id", filename)
    module.put("loaded", false)
    module.put("parent", parent.map(_.module).getOrElse(null))

    engine.eval( // Patch Object.create to unwrap ScriptObjectMirror to ScriptObject
      s"""(function() {
      var su = Java.type('${classOf[Modules].getName}');
      var c = Object.create;
      Object.create = function(p, v) { return c(su.unwrap(p), v); };
    })();""", global)

    private[Modules] def initialized: this.type = { module.put("loaded", true); this }

    def require(module: String): AnyRef = try {
      logger.debug(s"require('$module')")
      val found = (for {
        (folders, fname) <- splitPath(module)
        filenames = Vector(fname, fname + ".js", fname + ".json")
        found <-
          if(module.startsWith("/")  || module.startsWith("../")  || module.startsWith("./"))
            attemptToLoadStartingFromFolder(folder, folders, filenames)
          else {
            val folders2 = if(folders.startsWith("node_modules")) folders else "node_modules" +: folders
            val candidates = Iterator.iterate[Option[Folder]](Some(folder))(_.flatMap(_.parent)).takeWhile(_.isDefined).map(_.get).toVector
            // try the root folder first before moving up from the current folder:
            val cand2 = if(candidates.length < 2) candidates else candidates.last +: candidates.init.reverse
            candidates.map(attemptToLoadStartingFromFolder(_, folders2, filenames)).find(_.isDefined).flatten
          }
      } yield found).getOrElse(fail(module, s"Module not found: $module"))
      children.add(found.module)
      if(logger.isDebugEnabled) logger.debug(s"require('$module'): Exported " + (found.exports match {
        case s: ScriptObjectMirror => s"symbols: "+s.keySet().asScala.mkString(", ")
        case null                  => s"null"
        case o                     => s"object (${o.getClass.getName}): $o"
      }))
      found.exports
    } catch { case ex: Exception => fail(module, s"Error loading module: $module", ex) }

    private[this] def fail(module: String, msg: String, parent: Throwable = null): Nothing = {
      logger.warn(s"require('$module'): Error: $msg", parent)
      val error = engine.eval("Error").asInstanceOf[ScriptObjectMirror].newObject(msg).asInstanceOf[Bindings]
      error.put("code", "MODULE_NOT_FOUND");
      throw new ECMAException(error, null)
    }

    private[this] def attemptToLoadStartingFromFolder(from: Folder, folders: Vector[String], filenames: Vector[String]) =
      from.resolvePath(folders).flatMap(attemptToLoadFromThisFolder(_, filenames))

    private[this] def attemptToLoadFromThisFolder(from: Folder, filenames: Vector[String]) =
      filenames.iterator.map(loadModule(from, _)).find(_.isDefined).flatten

    private[this] def loadModule(parent: Folder, name: String): Option[Module] = {
      val fullPath = parent.path + name
      cache.getOrElseUpdate(fullPath,
        loadNativeModule(parent, fullPath, name)
          .orElse(loadModuleDirectly(parent, fullPath, name))
          .orElse(loadModuleThroughPackageJson(parent / name))
          .orElse(loadModuleThroughIndexJs(parent / name)))
    }

    private[this] def loadNativeModule(parent: Folder, fullPath: String, name: String) =
      resolveNative(parent.path + name).flatMap(compileAndCacheNative(parent, fullPath, _))

    private[this] def loadModuleDirectly(parent: Folder, fullPath: String, name: String) =
      resolve(parent.path + name).flatMap(compileAndCache(parent, fullPath, _))

    private[this] def loadModuleThroughPackageJson(parent: Folder) = for {
      packageJson <- resolve(parent.path + "package.json")
      mainFile <- Option(parseJson(packageJson).get("main").asInstanceOf[String])
      (path, filename) <- splitPath(mainFile)
      folder <- parent.resolvePath(path)
      code <- resolve(folder.path + filename)
      m <- compileAndCache(folder, folder.path + filename, code)
    } yield m

    private[this] def loadModuleThroughIndexJs(parent: Folder) =
      resolve(parent.path + "index.js").flatMap(compileAndCache(parent, parent.path + "index.js", _))

    private[this] def compileAndCache(parent: Folder, fullPath: String, code: String) = cache.getOrElseUpdate(fullPath, {
      val p = fullPath.toLowerCase()
      if(p.endsWith(".js")) Some(new JavaScriptModule(parent, fullPath, code, this))
      else if(p.endsWith(".json")) Some(new JSONModule(parent, fullPath, code, this))
      else None
    })

    private[this] def compileAndCacheNative(parent: Folder, fullPath: String, exp: AnyRef) =
      cache.getOrElseUpdate(fullPath, Some(new NativeModule(parent, fullPath, exp, this)))
  }

  private class JavaScriptModule(parent: Folder, fullPath: String, code: String, parentMod: Module)
  extends Module(parent, fullPath, new SimpleBindings(new JHashMap(engine.getBindings(ScriptContext.ENGINE_SCOPE))), Some(parentMod)) {
    engine.eval(code, global)
    exports = module.get("exports")
    initialized
  }

  private class JSONModule(parent: Folder, fullPath: String, code: String, parentMod: Module)
  extends Module(parent, fullPath, new SimpleBindings, Some(parentMod)) {
    exports = parseJson(code)
    initialized
  }

  private class NativeModule(parent: Folder, fullPath: String, exp: AnyRef, parentMod: Module)
    extends Module(parent, fullPath, new SimpleBindings, Some(parentMod)) {
    exports = exp
    initialized
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

  private def splitPath(path: String): Option[(Vector[String], String)] = if(path eq null) None else {
    val v = path.split("[\\\\/]").toVector
    if(v.length == 0) None else Some((v.init, v.last))
  }
}

case class Folder(parent: Option[Folder], path: String) {
  def / (name: String): Folder = Folder(Some(this), s"$path$name/")
  def resolvePath(path: Vector[String]) = path.foldLeft(Option(this)) {
    case (_, "") => throw new IllegalArgumentException
    case (z, ".") => z
    case (z, "..") => z.flatMap(_.parent)
    case (z, n) => z.map(_ / n)
  }
}

@FunctionalInterface trait RequireFunction { def require(module: String): AnyRef }
