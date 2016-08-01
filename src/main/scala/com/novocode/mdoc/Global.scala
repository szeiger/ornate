package com.novocode.mdoc

import java.net.URI

import com.novocode.mdoc.theme.Theme
import org.commonmark.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.parser.Parser.ParserExtension

import scala.collection.JavaConverters._
import better.files._
import com.typesafe.config.{ConfigValue, ConfigFactory, Config}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class Global(val config: GlobalConfig) {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  private[this] val cachedExtensions = new mutable.HashMap[String, Option[AnyRef]]

  private[this] def getCachedExtensionObject(ext: String): Option[AnyRef] = cachedExtensions.getOrElseUpdate(ext, {
    val className = config.aliasToExtension(ext)
    try {
      val cls = Class.forName(className)
      if(classOf[Extension].isAssignableFrom(cls))
        Some(cls.newInstance().asInstanceOf[Extension])
      else // CommonMark extension
        Some(cls.getMethod("create").invoke(null))
    } catch { case ex: Exception =>
      logger.error(s"Error instantiating extension class $className -- disabling extension", ex)
      None
    }
  })

  def getExtensions(names: Iterable[String]): Extensions = {
    val b = new ListBuffer[String]
    names.foreach { s =>
      if(s.startsWith("-")) b -= config.aliasToExtension(s.substring(1))
      else b += config.aliasToExtension(s)
    }
    val normalized = b.map(config.extensionToAlias).toVector
    new Extensions(normalized.map(a => (a, getCachedExtensionObject(a))))
  }

  def createTheme: Theme = {
    val cl = config.themeClass
    logger.debug(s"Creating theme from class $cl")
    Class.forName(cl).getConstructor(classOf[Global]).newInstance(this).asInstanceOf[Theme]
  }
}

class Extensions(extensions: Vector[(String, Option[AnyRef])]) {
  override def toString: String = extensions.map {
    case (name, None) => s"($name)"
    case (name, Some(o)) =>
      val types = Seq(
        if(o.isInstanceOf[Extension]) Some("e") else None,
        if(o.isInstanceOf[ParserExtension]) Some("p") else None,
        if(o.isInstanceOf[HtmlRendererExtension]) Some("h") else None
      ).flatten
      if(types.isEmpty) name else types.mkString(s"$name[", ",", "]")
  }.mkString(", ")

  def parser: Vector[ParserExtension] =
    extensions.collect { case (_, Some(e: ParserExtension)) => e }

  def htmlRenderer: Vector[HtmlRendererExtension] =
    extensions.collect { case (_, Some(e: HtmlRendererExtension)) => e }

  def mdoc: Vector[Extension] =
    extensions.collect { case (_, Some(e: Extension)) => e }
}

class GlobalConfig(startDir: File, confFile: File) {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  val config = {
    val ref = ConfigFactory.parseResources(getClass, "/mdoc-reference.conf")
    if(confFile.exists) {
      val c = ConfigFactory.parseFile(confFile.toJava)
      logger.debug(s"Using global configuration from $confFile: $c")
      c.withFallback(ref).resolve()
    } else {
      logger.debug(s"Global configuration $confFile not found, using defaults from reference.conf")
      ref.resolve()
    }
  }

  val sourceDir: File = {
    val docsrc = startDir / config.getString("global.srcdir")
    logger.debug(s"Source dir is: $docsrc")
    docsrc
  }

  val targetDir: File = {
    val doctarget = startDir / config.getString("global.targetdir")
    logger.debug(s"Target dir is: $doctarget")
    doctarget
  }

  val tocMaxLevel: Int = config.getInt("global.tocMaxLevel")

  val tocMergeFirst: Boolean = config.getBoolean("global.tocMergeFirst")

  val themeClass = {
    val aliasToTheme =
      config.getObject("global.themeAliases").unwrapped().asScala.toMap.mapValues(_.toString).withDefault(identity)
    aliasToTheme(config.getString("global.theme"))
  }

  def parsePageConfig(hocon: String): Config =
    ConfigFactory.parseString(hocon).withFallback(config).resolve()

  val (aliasToExtension: (String => String), extensionToAlias: (String => String)) = {
    val m = config.getObject("global.extensionAliases").unwrapped().asScala.toMap.mapValues(_.toString)
    (m.withDefault(identity), m.map { case (a, e) => (e, a) }.withDefault(identity))
  }

  val toc: Option[Vector[ConfigValue]] =
    if(config.hasPath("global.toc")) Some(config.getList("global.toc").asScala.toVector)
    else None
}
