package com.novocode.mdoc.theme

import java.io.FileNotFoundException
import java.net.{URL, URI}

import com.novocode.mdoc._
import com.novocode.mdoc.commonmark.NodeExtensionMethods._

import better.files._
import com.novocode.mdoc.config.Global
import org.webjars.WebJarAssetLocator

import scala.collection.JavaConverters._

/** Base class for themes. */
abstract class Theme(global: Global) extends Logging {

  /** Render the site. May create additional synthetic pages and copy resources on demand. */
  def render(site: Site): Unit

  /** Synthesize configured synthetic pages pre-TOC. Not all requested pages have to be
    * created but only the ones that are returned will be available for resolving the TOC. */
  def synthesizePages(uris: Vector[(String, URI)]): Vector[Page] = Vector.empty

  /** Get synthetic page names and the mapped URIs for pages that should be created by the theme.
    * Any pages that have to be created before resolving the TOC should be part of this. */
  def syntheticPageURIs: Vector[(String, URI)] = {
    if(global.userConfig.theme.config.hasPath("global.pages")) {
      val co = global.userConfig.theme.config.getObject("global.pages")
      co.entrySet().asScala.iterator.filter(_.getValue.unwrapped ne null).map(e =>
        (e.getKey, Util.siteRootURI.resolve(e.getValue.unwrapped.asInstanceOf[String]))
      ).toVector
    } else Vector.empty
  }

  private[this] lazy val locator = new WebJarAssetLocator()

  /** Resolve a resource URI to a URL. Resource URIs can use use the following protocols:
    * file, site (static site resources), webjar (absolute WebJar resource), theme (relative
    * to theme class), classpath (relative to classpath root) */
  def resolveResource(uri: URI): URL = uri.getScheme match {
    case "file" => uri.toURL
    case "site" => global.userConfig.resourceDir.path.toUri.resolve(uri.getPath.replaceFirst("^/*", "")).toURL
    case "webjar" =>
      val parts = uri.getPath.split('/').filter(_.nonEmpty)
      val path = locator.getFullPathExact(parts.head, parts.tail.mkString("/"))
      if(path eq null) throw new FileNotFoundException("WebJar resource not found: "+uri)
      getClass.getClassLoader.getResource(path)
    case "theme" =>
      val url = getClass.getResource(uri.getPath.replaceFirst("^/*", ""))
      if(url eq null) throw new FileNotFoundException("Theme resource not found: "+uri)
      url
    case "classpath" =>
      val url = getClass.getClassLoader.getResource(uri.getPath)
      if(url eq null) throw new FileNotFoundException("Classpath resource not found: "+uri)
      url
    case _ => throw new IllegalArgumentException("Unsupported scheme in resource URI "+uri)
  }

  /** Get a default relative path for a resource URI */
  def suggestRelativePath(uri: URI): String = uri.getScheme match {
    case "file" => uri.getPath.split('/').last
    case "site" => global.userConfig.resourceDir.path.toUri.resolve(uri.getPath).getPath.replaceFirst("^/*", "")
    case _ => uri.getPath.replaceFirst("^/*", "")
  }
}

/** A theme that prints the document structure to stdout. */
class Dump(global: Global) extends Theme(global) {
  def render(site: Site): Unit = {
    site.pages.foreach { p =>
      println("---------- Page: "+p.uri)
      p.doc.dumpDoc()
    }
  }
}
