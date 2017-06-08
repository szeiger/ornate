# Introduction

*Ornate* is a tool for building multi-page HTML sites (like this one) from Markdown sources. The design goals are:

- Runs on the JVM; No installation required: Resolve the versioned dependencies in your build process for reproducible documentation builds with no external dependencies.
- Based on [CommonMark], a standardized version of [Markdown].
- Use well-established extensions: In particular, many developers are already familiar with [Github-flavored Markdown][ghfm].
- Graceful degradation: Some features require proprietary syntax extensions. They should degrade gracefully when processed with a pure CommonMark engine.
- Configuration based on [Typesafe Config][config].
- Modular design: Themes, templates, [highlighters](highlighting.md), extensions - everything can be customized with Scala code.
- Clean, modern [default theme](default-theme.md) using responsive design for a good user experience on all devices from mobile phones to desktop PCs.

## Release Notes

### v0.5

- Fix sbt scoping ([](issue:9))
- Avoid conflicts with [sbt-site](https://github.com/sbt/sbt-site) ([](issue:6))

### v0.4

- Math notation in fenced code blocks
- Printer-friendly style for default theme
- Multi-threaded parsing and rendering
- [Subscript and superscript extensions](markdown.md#subscript-and-superscript)
- [Smart quotes](markdown.md#smartquotes) and [smart punctuation](markdown.md#smartpunctuation)
- Automatic ordering of extensions
- Configurable top and bottom navigation bars
- [Foundation Icons anf Font Awesome support](images.md#theme-specific-uri-schemes)
- [Easy customization of default theme (colors, `link` elements, CSS)](default-theme.md#customization)
- [Index support](default-theme.md#index)
- [Automatic multi-version navigation](default-theme.md#multi-version-sites)
- [Render included Markdown snippets](markdown.md#rendermarkdown)
- [External non-scaladoc links](markdown.md#externallinks)
- Bug fixes and minor features

### v0.3

- [Math support via MathJax](math.md)
- [Mermaid diagrams support](diagrams.md)
- [Minification of HTML, CSS and JavaScript](default-theme.md#minification)
- [Scaladoc links support](markdown.md#scaladoclinks)
- [Snippet source links](default-theme.md#snippet-source-links)
- [Target directory cleaning](running.md#cleaning)

### v0.2

- [Elasticlunr-based search](default-theme.md#search)
- [Global reference targets](markdown.md#globalrefs)

### v0.1

- First published release

## Terminology

- **Page**: A page comes from a single CommonMark source file. It has a source URI and a page URI in the `site:` namespace. Themes can also create synthetic pages which are not associated with any source file.

- **Resource**: A file which is copied verbatim to the generated site. Resources share the `site:` namespace with pages. They can be provided as part of sources (together with pages) or be generated during processing.

- **TOC**: The table of contents is built from a global configuration and the sections of all pages.

- **Site**: The site consists of all pages and resources plus the computed TOC.

- **Extension**: An extension can be a parser and/or renderer extension for [commonmark-java](https://github.com/atlassian/commonmark-java) or an Ornate extension for page processing. Extensions can be enabled separately for each page.

- **Theme**: A theme is a class that renders the site in some way. The default themes generates HTML files and copies resources but themes are free to do whatever they want with the site.
