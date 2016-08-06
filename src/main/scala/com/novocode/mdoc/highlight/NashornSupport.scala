package com.novocode.mdoc.highlight

import javax.script.ScriptEngineManager
import jdk.nashorn.api.scripting.{NashornScriptEngine, JSObject}
import org.webjars.WebJarAssetLocator
import better.files._
import scala.collection.JavaConverters._
import scala.io.Codec

/** Some utilities for running JavaScript code on Nashorn. */
trait NashornSupport {
  val engine = new ScriptEngineManager(null).getEngineByName("nashorn").asInstanceOf[NashornScriptEngine]
  val locator = new WebJarAssetLocator()

  def loadAsset(webjar: String, exactPath: String): Option[String] = {
    val path = locator.getFullPathExact(webjar, exactPath)
    if(path eq null) None
    else {
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

  def requireAsset(webjar: String, exactPath: String): AnyRef =
    require(loadAsset(webjar, exactPath).getOrElse(throw new Exception(s"WebJar resource $webjar:$exactPath not found")))

  def require(js: String): AnyRef = {
    engine.eval("var module = { exports: {} }, exports = module.exports;")
    engine.eval(js)
    engine.eval("module.exports")
  }

  def call[T](thiz: AnyRef, name: String, args: AnyRef*)(implicit ev: JSResultConverter[T]): T =
    ev(engine.invokeMethod(thiz, name, args: _*))
}

trait JSResultConverter[T] extends (AnyRef => T)
trait JSMap {
  def apply[T : JSResultConverter](key: String): T
}

object JSResultConverter {
  def apply[T](f: AnyRef => T): JSResultConverter[T] = new JSResultConverter[T] {
    def apply(v: AnyRef): T = f(v)
  }
  implicit val jsResultUnit = JSResultConverter(_ => ())
  implicit val jsResultString = JSResultConverter[String](o => if(o eq null) null else o.toString)
  implicit def jsResultVector[T](implicit ev: JSResultConverter[T]) =
    JSResultConverter[Vector[T]](_.asInstanceOf[JSObject].values.iterator().asScala.map(ev).toVector)
  implicit val jsResultMap = JSResultConverter[JSMap] { o =>
    val jo = o.asInstanceOf[JSObject]
    new JSMap {
      def apply[R](key: String)(implicit ev: JSResultConverter[R]): R =
        if(jo.hasMember(key)) ev(jo.getMember(key)) else null.asInstanceOf[R]
    }
  }
}
