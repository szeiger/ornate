package com.novocode.ornate

import com.novocode.ornate.commonmark.NodeExtensionMethods.nodeToNodeExtensionMethods
import com.novocode.ornate.config.ConfiguredObject
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.typesafe.config.{Config, ConfigObject}
import org.commonmark.node.{AbstractVisitor, Link, Text}

import scala.collection.JavaConverters._

/** Translate generic external links */
class ExternalLinksExtension(co: ConfiguredObject) extends Extension with Logging {
  case class Scheme(prefix: String, uriPattern: String, textPattern: Option[String])

  val schemesConfig = co.memoizeParsed { c =>
    c.root.entrySet().iterator().asScala.map { e =>
      e.getValue match {
        case v: ConfigObject =>
          val c = v.toConfig
          Scheme(e.getKey, c.getString("uri"), c.getStringOpt("text"))
        case v =>
          Scheme(e.getKey, String.valueOf(v.unwrapped()), None)
      }
    }.toVector
  }

  override def pageProcessors(site: Site) = Seq(new PageProcessor {
    def runAt: Phase = Phase.Expand
    def apply(p: Page): Unit = {
      val schemes = schemesConfig(p.config)
      if(schemes.nonEmpty) process(p, schemes)
    }
  })

  def process(p: Page, schemes: Seq[Scheme]): Unit = {
    p.doc.accept(new AbstractVisitor {
      override def visit(n: Link): Unit = {
        val dest = n.getDestination
        schemes.find(scheme => dest.startsWith(scheme.prefix + ":")).foreach { scheme =>
          logger.debug(s"Processing external link <$dest> with $scheme")
          val schemeSpecific = dest.substring(scheme.prefix.length+1)
          n.setDestination(scheme.uriPattern.replace("[all]", schemeSpecific))
          scheme.textPattern.foreach { p =>
            if(n.getFirstChild eq null)
              n.appendChild(new Text(p.replace("[all]", schemeSpecific)))
          }
        }
        super.visit(n)
      }
    })
  }
}
