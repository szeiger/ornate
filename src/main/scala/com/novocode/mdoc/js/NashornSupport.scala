package com.novocode.mdoc.js

import java.util
import java.util.Locale
import javax.script.{ScriptContext, SimpleBindings, Bindings, ScriptEngineManager}

import better.files._
import com.novocode.mdoc.Logging
import jdk.nashorn.api.scripting._
import org.slf4j.{LoggerFactory, Logger}
import org.webjars.WebJarAssetLocator

import scala.collection.JavaConverters._
import scala.io.Codec

/** Some utilities for running JavaScript code on Nashorn. */
trait NashornSupport { this: Logging =>
  import NashornSupport._

  val engine = new NashornScriptEngineFactory().getScriptEngine("--const-as-var"
    /*, "--global-per-engine"*/).asInstanceOf[NashornScriptEngine]
  val locator = new WebJarAssetLocator()
  val mainModule = new Modules(engine) {
    val PATHSPEC = """/node_modules/([^/]*)(?:(/.*))?""".r
    override def resolve(path: String): Option[String] = path match {
      case s @ PATHSPEC(module, pOpt) =>
        val p = if((pOpt eq null) || pOpt.isEmpty) "" else pOpt.drop(1)
        logger.debug(s"Looking for WebJar asset: $module:/$p")
        val a = loadAsset(module, p)
        if(p.toLowerCase.endsWith(".js")) a.map(js => s"//# sourceURL=/$module/$p\n$js")
        else a
      case s =>
        logger.debug(s"Unmatched path in require(): $s")
        None
    }
    override def resolveNative(path: String): Option[AnyRef] = path match {
      case "/node_modules/console" =>
        logger.debug("Creating native module: console")
        Some(createConsole(logger))
      case _ => None
    }
  }.main

  def loadAsset(webjar: String, exactPath: String): Option[String] = {
    val path = locator.getFullPathExact(webjar, exactPath)
    if(path eq null) {
      //logger.debug(s"WebJar asset not found: $webjar:/$exactPath")
      None
    } else {
      logger.debug(s"Loading WebJar asset: $webjar:/$exactPath")
      val in = getClass.getClassLoader.getResourceAsStream(path)
      try Some(in.content(Codec.UTF8).mkString) finally in.close()
    }
  }

  def listAssets(webjar: String, path: String): Vector[String] = {
    val maybeVersion = locator.getWebJars.get(webjar)
    val prefix = WebJarAssetLocator.WEBJARS_PATH_PREFIX + "/" + webjar + (if(maybeVersion eq null) "" else s"/$maybeVersion")
    val absPath = if(path.startsWith("/")) path else "/"+path
    val prefixedPath = prefix + absPath
    locator.listAssets(prefixedPath).asScala.iterator.map(_.substring(prefixedPath.length)).toVector
  }

  def call[T](thiz: AnyRef, name: String, args: AnyRef*)(implicit ev: JSResultConverter[T]): T =
    ev(engine.invokeMethod(thiz, name, args: _*))

  def engineBindings: Bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
}

object NashornSupport {
  def jsFunc(f: Seq[Any] => Any): JSObject = new AbstractJSObject {
    override def isFunction = true
    override def call(thiz: Any, args: AnyRef*): AnyRef = f(args.toSeq).asInstanceOf[AnyRef]
  }

  def bindings(entries: (String, Any)*): Bindings = {
    val b = new SimpleBindings()
    for((k, v) <- entries) b.put(k, v)
    b
  }

  private def createConsole(logger: Logger): Bindings = {
    val jslog = LoggerFactory.getLogger(logger.getName + ".js")
    val jsclog = LoggerFactory.getLogger(logger.getName + ".jslog")
    def format(args: Seq[Any]): String = args.length match {
      case 0 => "null"
      case 1 => String.valueOf(args.head)
      case _ =>
        new util.Formatter(Locale.ENGLISH).format(String.valueOf(args(0)),
          args.tail.asInstanceOf[Seq[AnyRef]].toArray: _*).out.toString
    }
    bindings(
      "log"   -> jsFunc(args => if(jsclog.isInfoEnabled) jsclog.info(format(args))),
      "debug" -> jsFunc(args => if(jslog.isDebugEnabled) jslog.debug(format(args))),
      "info"  -> jsFunc(args => if(jslog.isInfoEnabled) jslog.info(format(args))),
      "warn"  -> jsFunc(args => if(jslog.isWarnEnabled) jslog.warn(format(args))),
      "error" -> jsFunc(args => if(jslog.isErrorEnabled) jslog.error(format(args))),
      "trace" -> jsFunc(args => if(jslog.isDebugEnabled) jslog.debug("console.trace()", new Throwable("console.trace()")))
    )
  }
}
