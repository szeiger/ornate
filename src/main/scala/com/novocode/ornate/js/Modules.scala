package com.novocode.ornate.js

import com.novocode.ornate.Logging
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.JavaConverters._

import java.util.{ArrayList => JArrayList}
import javax.script.{Bindings, ScriptContext, SimpleBindings}
import jdk.nashorn.api.scripting.{NashornScriptEngine, ScriptObjectMirror}
import jdk.nashorn.internal.runtime.ECMAException

class Modules(engine: NashornScriptEngine) {
  val logger = LoggerFactory.getLogger(classOf[Modules])
  private val cache = new mutable.HashMap[Vector[String], Module]

  val main: JSModule = {
    engine.put("global", engine.getBindings(ScriptContext.ENGINE_SCOPE))
    val m = new JSModule(Vector("main"), None, None)
    engine.put("require", m.requireFunc)
    m
  }

  def resolve(path: Vector[String]): Option[String] = None
  def resolveCore(name: String): Option[AnyRef] = None

  abstract class Module {
    private[Modules] val module = engine.createBindings
    def exports: AnyRef
  }

  class ImmediateModule(val exports: AnyRef) extends Module {
    module.put("exports", exports)
    module.put("loaded", true)
  }

  class JSModule(path: Vector[String], val parent: Option[Module], code: Option[String]) extends Module { self =>
    private[this] var _exports: AnyRef = engine.createBindings
    def exports = _exports
    private[this] val _children = new JArrayList[Bindings]
    private[Modules] val requireFunc = new SimpleBindings with RequireFunction {
      put("main", (if(parent.isDefined) main else self).module)
      def require(module: String): AnyRef = self.require(module)
    }
    val id = path.mkString("/")

    module.put("children", _children)
    module.put("exports", exports)
    module.put("filename", id)
    module.put("id", id)
    module.put("loaded", false)
    module.put("parent", parent.map(_.module).getOrElse(null))

    code.foreach { js =>
      val f = engine.eval("(function(exports, require, module, __filename, __dirname) { //# sourceURL=" +
        path.mkString("/") + "\n" + js + "\n});")
      engine.invokeMethod(f, "call", f, _exports, requireFunc, module, id, path.init.mkString("/"))
      _exports = module.get("exports")
    }
    module.put("loaded", true)

    def require(module: String): AnyRef = {
      val exports = try {
        logger.debug(s"require('$module')")
        // Lookup algorithm based on https://nodejs.org/api/modules.html#modules_all_together
        loadCoreModule(module).orElse {
          if(module.startsWith("/")  || module.startsWith("../")  || module.startsWith("./")) {
            val abs = resolvePath(path.init, module.split('/'))
            abs.flatMap(loadAsFile _).orElse(abs.flatMap(loadAsDirectory _))
          } else None
        }.orElse(loadNodeModules(module.split('/'), path.init)).map { f =>
          _children.add(f.module)
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
      logger.warn(s"require('$module'): $msg", parent)
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
        Option(parseJson(s).get("main").asInstanceOf[String]).flatMap(m => resolvePath(path, m.split('/')).flatMap { p =>
          // The documented algorithm only does loadAsFile but in practice some modules require loadAsDirectory
          loadAsFile(p).orElse(loadAsDirectory(p))
        })
      }.orElse(loadJSModule(path :+ "index.js")).orElse(loadJSONModule(path :+ "index.json"))

    private[this] def loadNodeModules(path: Iterable[String], start: Vector[String]): Option[Module] =
      start.inits.filterNot(_.endsWith("node_modules")).map(_ :+ "node_modules").map { d =>
        loadAsFile(d ++ path).orElse(loadAsDirectory(d ++ path))
      }.find(_.isDefined).flatten

    private[this] def loadJSModule(path: Vector[String]) =
      cached(path)(p => resolve(p).map(code => new JSModule(p, Some(this), Some(code))))

    private[this] def loadJSONModule(path: Vector[String]) =
      cached(path)(p => resolve(p).map(code => new ImmediateModule(parseJson(code))))

    private[this] def loadCoreModule(name: String) =
      cached(Vector(name))(p => resolveCore(name).map(exp => new ImmediateModule(exp)))
  }

  private def cached(path: Vector[String])(f: Vector[String] => Option[Module]) = cache.get(path).orElse {
    val mo = f(path)
    mo.foreach(cache.put(path, _))
    mo
  }

  private def parseJson(json: String) =
    engine.eval("JSON").asInstanceOf[ScriptObjectMirror].callMember("parse", json).asInstanceOf[ScriptObjectMirror]

  private def resolvePath(base: Vector[String], rel: Iterable[String]): Option[Vector[String]] = rel.foldLeft(Option(base)) {
    case (z, ("" | ".")) => z
    case (z, "..") => z.flatMap(zz => if(zz.length > 0) Some(zz.dropRight(1)) else None)
    case (z, n) => z.map(_ :+ n)
  }
}

@FunctionalInterface trait RequireFunction { def require(module: String): AnyRef }
