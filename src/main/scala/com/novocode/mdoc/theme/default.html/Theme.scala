package com.novocode.mdoc.theme.default.html

import java.net.URI

import com.novocode.mdoc.config.Global
import com.novocode.mdoc.{PageParser, Page, Util}
import com.novocode.mdoc.theme.HtmlTheme

class Theme(global: Global) extends HtmlTheme(global) {

  override def synthesizePages(uris: Vector[(String, URI)]): Vector[Page] = {
    uris.flatMap {
      case ("toc", u) =>
        logger.debug(s"Creating TOC page $u")
        Some(PageParser.parseContent(global.referenceConfig, u, ".md",
          "# Table of Contents\n\n![Table of Contents](toctree:)",
          global.userConfig.parsePageConfig("title: Table of Contents"), true))
      case (name, u) =>
        logger.warn(s"""Unknown extra page name "$name" in theme configuration -- ignoring""")
        None
    }
  }
}
