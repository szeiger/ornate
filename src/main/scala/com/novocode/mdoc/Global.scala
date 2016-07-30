package com.novocode.mdoc

import java.net.URI

import com.novocode.mdoc.theme.Theme

import scala.collection.JavaConverters._
import better.files._
import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class Global(startDir: File) {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  val docRootURI = new URI("doc", null, "/", null)

  val config = {
    val ref = ConfigFactory.parseResources(getClass, "/mdoc-reference.conf")
    val confFile = startDir / "mdoc.conf"
    if(confFile.exists) {
      val c = ConfigFactory.parseFile(confFile.toJava)
      logger.debug(s"Using global configuration from $confFile: $c")
      c.withFallback(ref).resolve()
    } else {
      logger.debug(s"Global configuration $confFile not found, using defaults from reference.conf")
      ref.resolve()
    }
  }

  lazy val sourceDir: File = {
    val docsrc = startDir / config.getString("global.srcdir")
    logger.debug(s"Source dir is: $docsrc")
    docsrc
  }

  lazy val targetDir: File = {
    val doctarget = startDir / config.getString("global.targetdir")
    logger.debug(s"Target dir is: $doctarget")
    doctarget
  }

  lazy val tocMaxLevel: Int = config.getInt("global.tocMaxLevel")

  def parsePageConfig(hocon: String): Config =
    ConfigFactory.parseString(hocon).withFallback(config).resolve()

  private[this] val aliasToExtension: Map[String, String] =
    config.getObject("global.extensionAliases").unwrapped().asScala.toMap.mapValues(_.toString).withDefault(identity)

  private[this] val extensionToAlias: Map[String, String] = aliasToExtension.map { case (a, e) => (e, a) }.withDefault(identity)

  def normalizeExtensions(raw: Iterable[String]): Vector[String] = {
    val b = new ListBuffer[String]
    raw.foreach { s =>
      if(s.startsWith("-")) b -= aliasToExtension(s.substring(1))
      else b += aliasToExtension(s)
    }
    b.map(extensionToAlias).toVector
  }

  private[this] val cachedExtensions = new mutable.HashMap[String, Option[AnyRef]]

  def getCachedExtensionObject(ext: String): Option[AnyRef] = cachedExtensions.getOrElseUpdate(ext, {
    val className = aliasToExtension(ext)
    val cls = Class.forName(className)
    try {
      if(classOf[ExtensionFactory].isAssignableFrom(cls)) // MDoc extension
        Some(cls.newInstance().asInstanceOf[ExtensionFactory].apply(this))
      else // CommonMark extension
        Some(cls.getMethod("create").invoke(null))
    } catch { case ex: Exception =>
      logger.error(s"Error instantiating extension class $className -- disabling extension", ex)
      None
    }
  })

  private[this] val aliasToTheme: Map[String, String] =
    config.getObject("global.themeAliases").unwrapped().asScala.toMap.mapValues(_.toString).withDefault(identity)

  def createTheme(site: Site): Theme = {
    val cl = aliasToTheme(config.getString("global.theme"))
    logger.debug(s"Creating theme from class $cl")
    Class.forName(cl).getConstructor(classOf[Site], classOf[Global]).newInstance(site, this).asInstanceOf[Theme]
  }
}
