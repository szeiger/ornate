package com.novocode.ornate

import com.novocode.ornate.commonmark.PageProcessor
import com.novocode.ornate.commonmark.NodeExtensionMethods.nodeToNodeExtensionMethods
import com.novocode.ornate.config.ConfiguredObject
import com.typesafe.config.{ConfigObject, Config}
import org.commonmark.node.{Text, Link, AbstractVisitor}

import scala.collection.JavaConverters._

/** Translate scaladoc links */
class ScaladocLinksExtension(co: ConfiguredObject) extends Extension with Logging {
  case class Scheme(prefix: String, indexUri: String)

  val schemesConfig = co.memoizeParsed { c =>
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

  override def pageProcessors(site: Site) = Seq(new PageProcessor {
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
          logger.debug(s"Processing scaladoc link <$dest> with $scheme")
          val fragment = dest.substring(scheme.prefix.length+1)
          n.setDestination(scheme.indexUri + "#" + fragment)
          val ch = n.getFirstChild
          if(ch eq null)
            n.appendChild(new Text(autoName(fragment)))
          else if(ch.isInstanceOf[Text] && ch.getNext == null && ch.asInstanceOf[Text].getLiteral == dest)
            ch.asInstanceOf[Text].setLiteral(autoName(fragment))
        }
        super.visit(n)
      }
    })
  }

  def autoName(fragment: String): String = {
    fragment.indexOf('@') match {
      case -1 =>
        fragment.split('.').filter(_.nonEmpty).lastOption.flatMap(_.split('$').filter(_.nonEmpty).lastOption).getOrElse(fragment)
      case i =>
        val member = fragment.substring(i+1)
        val memberName = Seq(member.indexOf(':'), member.indexOf('['), member.indexOf('(')).filter(_ >= 0) match {
          case Seq() => member
          case s => member.substring(0, s.min)
        }
        if(memberName.nonEmpty) memberName
        else if(member.nonEmpty) member
        else fragment
    }
  }
}
