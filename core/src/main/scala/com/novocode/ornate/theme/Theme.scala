package com.novocode.ornate.theme

import java.io.FileNotFoundException
import java.net.{URI, URL}

import scala.collection.mutable
import com.novocode.ornate._
import com.novocode.ornate.commonmark.{AttributeFencedCodeBlocksProcessor, SpecialImageProcessor}
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods
import com.novocode.ornate.config.Global
import com.novocode.ornate.js.WebJarSupport
import better.files._

/** Base class for themes. */
abstract class Theme(val global: Global) extends Logging {

  /** The full pipeline for building the site. */
  def build: Unit = {
    val pages = buildAllPages

    logger.info("Processing site")
    val toc = TocParser.parse(global.userConfig, pages)
    val site = new Site(pages, toc)

    val sip = new SpecialImageProcessor(global.userConfig, specialImageSchemesInline, specialImageSchemesBlock)
    logTime("Running page processors took") {
      global.parMap(pages) { p =>
        val pagepp = p.extensions.ornate.flatMap(_.pageProcessors(site))
        p.processors = (AttributeFencedCodeBlocksProcessor +: sip +: pagepp).sortBy(_.runAt.idx)
        p.applyProcessors()
      }
    }

    val etp = new ExpandTocProcessor(toc)
    pages.foreach(etp)

    logger.info("Rendering site to "+global.userConfig.targetDir)
    render(site)
  }

  /** Extra image URI schemes to turn into `SpecialImage` nodes for rendering in inline contexts */
  def specialImageSchemesInline: Set[String] = Set.empty

  /** Extra image URI schemes to turn into `SpecialImage` nodes for rendering in block contexts */
  def specialImageSchemesBlock: Set[String] = Set.empty

  /** Render the site. May create additional synthetic pages and copy resources on demand. */
  def render(site: Site): Unit

  /** Get all source pages and synthetic pages */
  def buildAllPages: Vector[Page] = PageParser.parseSources(global) ++ synthesizePages

  /** Synthesize configured synthetic pages pre-TOC. Not all requested pages have to be
    * created but only the ones that are returned will be available for resolving the TOC. */
  def synthesizePages: Vector[Page] = Vector.empty

  /** Get synthetic page names and the mapped URIs for pages that should be created by the theme.
    * Any pages that have to be created before resolving the TOC should be part of this. */
  protected def syntheticPageURIs: Vector[(String, URI)] =
    global.userConfig.theme.config.getConfigMapOr("global.pages").iterator.filter(_._2.unwrapped ne null).map(e =>
      (e._1, Util.siteRootURI.resolve(e._2.unwrapped.asInstanceOf[String]))
    ).toVector

  /** Resolve a resource URI to a source file location. Resource URIs can use use the following protocols:
    * file, site (static site resources), webjar (absolute WebJar resource), theme (relative to theme class),
    * classpath (relative to classpath root), template (generated from template) */
  def resolveResource(uri: URI): URI = uri.getScheme match {
    case "file" | "template" => uri
    case "site" => global.userConfig.resourceDir.path.toUri.resolve(uri.getPath.replaceFirst("^/*", ""))
    case "webjar" =>
      val parts = uri.getPath.split('/').filter(_.nonEmpty)
      val path = WebJarSupport.getFullPathExact(parts.head, parts.tail.mkString("/"))
      if(path.isEmpty) throw new FileNotFoundException("WebJar resource not found: "+uri)
      getClass.getClassLoader.getResource(path.get).toURI
    case "theme" =>
      val url = getClass.getResource(uri.getPath.replaceFirst("^/*", ""))
      if(url eq null) throw new FileNotFoundException("Theme resource not found: "+uri)
      url.toURI
    case "classpath" =>
      val url = getClass.getClassLoader.getResource(uri.getPath.replaceFirst("^/*", ""))
      if(url eq null) throw new FileNotFoundException("Classpath resource not found: "+uri)
      url.toURI
    case _ => throw new IllegalArgumentException("Unsupported scheme in resource URI "+uri)
  }

  /** Get a default relative path for a resource URI */
  def suggestRelativePath(uri: URI): String = uri.getScheme match {
    case "file" => uri.getPath.split('/').last
    case "site" => global.userConfig.resourceDir.path.toUri.resolve(uri.getPath).getPath.replaceFirst("^/*", "")
    case _ => uri.getPath.replaceFirst("^/*", "")
  }
}

class PageResources(val page: Page, theme: Theme, baseURI: URI) {
  private[this] val buf = new mutable.ArrayBuffer[ResourceSpec]
  private[this] val map = new mutable.HashMap[URI, ResourceSpec]

  final def get(path: String, targetFile: String = null, createLink: Boolean = false, minified: Boolean = false): URI =
    Util.relativeSiteURI(page.uri, getURI(Util.themeRootURI.resolve(path), targetFile, createLink, minified))

  final def getLinks(suffix: String): Iterable[URI] =
    mappings.collect { case r: ResourceSpec if r.createLink && r.targetURI.toString.endsWith("."+suffix) => Util.relativeSiteURI(page.uri, r.targetURI) }

  def mappings: Iterable[ResourceSpec] = buf

  def getURI(sourceURI: URI, targetFile: String, createLink: Boolean, minified: Boolean): URI = {
    try {
      val resolved = theme.resolveResource(sourceURI)
      map.getOrElseUpdate(resolved, {
        val targetURI =
          if(sourceURI.getScheme == "site") sourceURI // link to site resources at their original location
          else {
            val tname =
              if(targetFile eq null) {
                theme.suggestRelativePath(sourceURI).replaceAll("^/*", "")
              } else if(targetFile.endsWith("/")) {
                val s = theme.suggestRelativePath(sourceURI).replaceAll("^/*", "")
                if(s.startsWith(targetFile)) s else targetFile + s
              } else targetFile.replaceAll("^/*", "")
            baseURI.resolve(tname)
          }
        val spec = ResourceSpec(sourceURI, resolved, targetURI, createLink, this, minified)
        buf += spec
        spec
      }).targetURI
    } catch { case ex: Exception =>
      theme.logger.error(s"Error resolving theme resource URI $sourceURI -- Skipping resource and using original link", ex)
      sourceURI
    }
  }
}

/** Resource to include in the generated site.
  *
  * @param sourceURI The source URI
  * @param resolvedSourceURI the resolved source URI to locate the file
  * @param targetURI the target `site:` URI
  * @param createLink whether to create a link to the resource (e.g. "script" or "style" tag)
  * @param resources the `Resources` object which created this ResourceSpec
  * @param minified whether the resource is already minified. In addition, all resources whose sourceURI path ends
  *                 with ".min" before the actual suffix are also considered minified.
  */
case class ResourceSpec(sourceURI: URI, resolvedSourceURI: URI, targetURI: URI, createLink: Boolean, resources: PageResources, minified: Boolean) {
  def minifiableType: Option[String] = if(minified) None else {
    val p = sourceURI.getPath
    val sep = p.lastIndexOf('.')
    if(sep <= 0 || sep >= p.length-1) None
    else {
      if(p.substring(0, sep).endsWith(".min")) None
      else Some(p.substring(sep+1))
    }
  }
}
