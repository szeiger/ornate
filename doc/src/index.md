# Introduction

*Ornate* is a tool for building multi-page HTML sites from Markdown sources. The design goals are:

- Runs on the JVM; No installation required: Resolve the versioned dependencies in your build process for reproducible documentation builds with no external dependencies.
- Based on [CommonMark], a standardized version of [Markdown].
- Use well-established extensions: In particular, many developers are already familiar with [Github-flavored Markdown][ghfm].
- Graceful degradation: Some features require proprietary syntax extensions. They should degrade gracefully when processed with a pure CommonMark engine.
- Configuration based on [Typesafe Config][config].
- Modular design: Themes, templates, [highlighters](highlighting.md), extensions - everything can be customized with Scala code.
- Clean, modern [default theme](default-theme.md) using responsive design for a good user experience on all devices from mobile phones to desktop PCs.

## Terminology

- **Page**: A page comes from a single CommonMark source file. It has a source URI and a page URI in the `site:` namespace. Themes can also create synthetic pages which are not associated with any source file.

- **Resource**: A file which is copied verbatim to the generated site. Resources share the `site:` namespace with pages. They can be provided as part of sources (together with pages) or be generated during processing.

- **TOC**: The table of contents is built from a global configuration and the sections of all pages.

- **Site**: The site consists of all pages and resources plus the computed TOC.

- **Extension**: An extension can be a parser and/or renderer extension for [commonmark-java](https://github.com/atlassian/commonmark-java) or an Ornate extension for page processing. Extensions can be enabled separately for each page.

- **Theme**: A theme is a class that renders the site in some way. The default themes generates HTML files and copies resources but themes are free to do whatever they want with the site.
