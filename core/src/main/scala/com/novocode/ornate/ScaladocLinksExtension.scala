package com.novocode.ornate

import com.novocode.ornate.commonmark.PageProcessor
import com.novocode.ornate.commonmark.NodeExtensionMethods.nodeToNodeExtensionMethods
import com.novocode.ornate.config.ConfiguredObject
import com.typesafe.config.{ConfigObject, Config}
import org.commonmark.node.{Text, Link, AbstractVisitor}

import scala.collection.JavaConverters._

/** Translate scaladoc links */
class ScaladocLinksExtension(co: ConfiguredObject) extends Extension with Logging {
  lazy val siteSchemes = parseSchemes(co.config)

  case class Scheme(prefix: String, indexUri: String)

  override def pageProcessors(site: Site) = Seq(new PageProcessor {
    def apply(p: Page): Unit = {
      val c = co.getConfig(p.config)
      // Schemes are usually defined in the site config. Reuse a cached site config unless the page config differs:
      val schemes = if(c eq co.config) siteSchemes else parseSchemes(c)
      if(schemes.nonEmpty) process(p, schemes)
    }
  })

  def process(p: Page, schemes: Seq[Scheme]): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Link): Unit = {
        val dest = n.getDestination
        schemes.find(scheme => dest.startsWith(scheme.prefix + ":")).foreach { scheme =>
          logger.debug(s"Processing scaladoc link <$dest> with $scheme")
          val fragment = dest.substring(scheme.prefix.length+1)
          n.setDestination(scheme.indexUri + "#" + fragment)
          if(n.getFirstChild eq null) {
            val t = new Text()
            t.setLiteral(autoName(fragment))
            n.appendChild(t)
          }
        }
        super.visit(n)
      }
    })
  }

  def autoName(fragment: String): String =
    fragment.split('.').filter(_.nonEmpty).lastOption.flatMap(_.split('$').filter(_.nonEmpty).lastOption).getOrElse(fragment)

  def parseSchemes(c: Config): Seq[Scheme] =
    c.root.entrySet().iterator().asScala.map { e =>
      e.getValue match {
        case v: ConfigObject =>
          val c = v.toConfig
          Scheme(e.getKey, c.getString("index"))
        case v =>
          Scheme(e.getKey, String.valueOf(v.unwrapped()))
      }
    }.toVector
}
