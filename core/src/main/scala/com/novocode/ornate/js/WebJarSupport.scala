package com.novocode.ornate.js

import java.util.regex.Pattern

import better.files._
import org.slf4j.Logger
import org.webjars.WebJarAssetLocator

import scala.collection.JavaConverters._
import scala.io.Codec

trait WebJarSupport {
  import WebJarSupport._

  def logger: Logger

  def loadAsset(webjar: String, exactPath: String): Option[String] = getFullPathExact(webjar, exactPath).map { path =>
    logger.debug(s"Loading WebJar asset: $webjar/$exactPath")
    val in = getClass.getClassLoader.getResourceAsStream(path)
    try in.content(Codec.UTF8).mkString finally in.close()
  }
}

object WebJarSupport {
  @volatile private lazy val fullPathIndex = {
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    val fpi = WebJarAssetLocator.getFullPathIndex(Pattern.compile(".*"), loader)
    val offset = WebJarAssetLocator.WEBJARS_PATH_PREFIX.length + 1
    fpi.asScala.valuesIterator.map { s =>
      val sep1 = s.indexOf('/', offset+1)
      val sep2 = s.indexOf('/', sep1+1)
      val noVer = s.substring(offset, sep1) + s.substring(sep2)
      (noVer, s)
    }.toMap
  }

  /* This is orders of magnitude faster than WebJarLocator.getFullPathExact: */
  def getFullPathExact(webjar: String, exactPath: String): Option[String] =
    fullPathIndex.get(webjar + "/" + exactPath)

  def listAssets(webjar: String, path: String): Vector[String] = {
    val prefix = if(path.startsWith("/")) webjar+path else webjar+"/"+path
    fullPathIndex.keysIterator.filter(_.startsWith(prefix)).map(_.substring(prefix.length)).toVector
  }
}
