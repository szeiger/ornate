package com.novocode.ornate.config

import java.util.Properties

import com.typesafe.config._
import play.api.libs.json.Json

import scala.collection.JavaConverters._

/** Extension methods to make Typesafe Config easier to use */
class ConfigExtensionMethods(val c: Config) extends AnyVal {
  def getBooleanOr(path: String, default: => Boolean = false) = if(c.hasPath(path)) c.getBoolean(path) else default
  def getIntOr(path: String, default: => Int = 0) = if(c.hasPath(path)) c.getInt(path) else default
  def getStringOr(path: String, default: => String = null) = if(c.hasPath(path)) c.getString(path) else default
  def getConfigOr(path: String, default: => Config = ConfigFactory.empty()) = if(c.hasPath(path)) c.getConfig(path) else default
  def getStringListOr(path: String, default: => Seq[String] = Vector.empty): Seq[String] = if(c.hasPath(path)) c.getStringList(path).asScala else default
  def getConfigListOr(path: String, default: => Seq[Config] = Vector.empty): Seq[Config] = if(c.hasPath(path)) c.getConfigList(path).asScala else default
  def getConfigMapOr(path: String, default: => Map[String, ConfigValue] = Map.empty): Map[String, ConfigValue] =
    if(c.hasPath(path)) c.getObject(path).entrySet().asScala.iterator.map(e => (e.getKey, e.getValue)).toMap else default

  def getAnyRefOpt(path: String): Option[AnyRef] = if(c.hasPath(path)) Some(c.getAnyRef(path)) else None
  def getBooleanOpt(path: String): Option[Boolean] = if(c.hasPath(path)) Some(c.getBoolean(path)) else None
  def getIntOpt(path: String): Option[Int] = if(c.hasPath(path)) Some(c.getInt(path)) else None
  def getStringOpt(path: String) = Option(getStringOr(path))
  def getConfigOpt(path: String): Option[Config] = Option(getConfigOr(path, null))
  def getListOpt(path: String): Option[Seq[ConfigValue]] = if(c.hasPath(path)) Some(c.getList(path).asScala) else None
  def getStringListOpt(path: String): Option[Seq[String]] = Option(getStringListOr(path, null))

  def getStringOrStringList(path: String, default: => Vector[String] = Vector.empty): Vector[String] =
    if(c.hasPath(path)) c.getAnyRef(path) match {
      case s: String => Vector(s)
      case i: java.util.Collection[_] => i.asScala.toVector.asInstanceOf[Vector[String]]
    } else default
}

object ConfigExtensionMethods {
  @inline implicit def configExtensionMethods(c: Config): ConfigExtensionMethods = new ConfigExtensionMethods(c)
}
