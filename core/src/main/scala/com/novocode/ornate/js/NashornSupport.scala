package com.novocode.ornate.js

import java.util
import java.util.Locale
import java.util.regex.Pattern
import javax.script.{ScriptContext, SimpleBindings, Bindings, ScriptEngineManager}

import better.files._
import jdk.nashorn.api.scripting._
import org.slf4j.{LoggerFactory, Logger}
import org.webjars.WebJarAssetLocator

import scala.collection.JavaConverters._
import scala.io.Codec

/** Some utilities for running JavaScript code on Nashorn. */
trait NashornSupport {
  import NashornSupport._

  def logger: Logger

  val engine = {
    val f = new NashornScriptEngineFactory
    // Older JDK8 versions have `getScriptEngine(Array[String])`, newer ones have `getScriptEngine(String...)`
    f.getClass.getMethod("getScriptEngine", classOf[Array[String]]).invoke(f, Array("--const-as-var"))
  }.asInstanceOf[NashornScriptEngine]

  val mainModule = new Modules(engine) {
    override def resolve(path: Vector[String]): Option[String] = path match {
      case Vector("node_modules", module, pElems @ _*) =>
        val p = pElems.mkString("/")
        //logger.debug(s"Looking for WebJar asset: $module/$p")
        val a = loadAsset(module, p)
        if(p.toLowerCase.endsWith(".js")) a.map(patchJavaScript(module, p, _))
        else a
      case s =>
        logger.debug(s"Unmatched path in require(): $s")
        None
    }
    override def resolveCore(path: String): Option[AnyRef] = path match {
      case "console" =>
        logger.debug("Creating native module: console")
        Some(createConsole(logger))
      case _ => None
    }
  }.main

  def patchJavaScript(module: String, path: String, js: String): String = {
    js
    //val js2 = if(js.startsWith(""""use strict";""")) js.substring(13) else js
  }

  def loadAsset(webjar: String, exactPath: String): Option[String] = {
    val path = locator.getFullPathExact(webjar, exactPath)
    if(path eq null) {
      //logger.debug(s"WebJar asset not found: $webjar/$exactPath")
      None
    } else {
      logger.debug(s"Loading WebJar asset: $webjar/$exactPath")
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

  def call[T](thiz: AnyRef, name: String, args: Any*)(implicit ev: JSResultConverter[T]): T =
    ev(engine.invokeMethod(thiz, name, args.asInstanceOf[Seq[AnyRef]]: _*))

  def engineBindings: Bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)

  private[this] lazy val json = engine.eval("JSON").asInstanceOf[ScriptObjectMirror]

  def parseJSON(jsonString: String): AnyRef = json.callMember("parse", jsonString)

  def stringifyJSON(value: AnyRef): String = json.callMember("stringify", value).asInstanceOf[String]
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

  lazy val locator = {
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    new WebJarAssetLocator(WebJarAssetLocator.getFullPathIndex(Pattern.compile(".*"), loader))
  }
}
