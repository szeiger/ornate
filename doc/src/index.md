# Introduction

*Ornate* is a tool for building multi-page HTML sites from Markdown sources. The design goals are:

- Runs on the JVM; No installation required: Resolve the versioned dependencies in your build process for reproducible documentation builds with no external dependencies.
- Based on [CommonMark](http://commonmark.org/), a standardized version of [Markdown](http://daringfireball.net/projects/markdown/).
- Use well-established extensions: In particular, many developers are already familiar with [Github-flavored Markdown](https://help.github.com/categories/writing-on-github/).
- Graceful degradation: Some features require proprietary syntax extensions. They should degrade gracefully when processed with a pure CommonMark engine.
- Configuration based on [Typesafe Config](https://github.com/typesafehub/config).
- Modular design: Themes, templates, [highlighters](highlighting.md), extensions - everything can be customized with Scala code.
- Clean, modern [default theme](default-theme.md) using responsive design for a good user experience on all devices from mobile phones to desktop PCs.

## Running

You can run Ornate from the command line via the class `com.novocode.ornate.Main`. The following arguments are supported:

- `[--base-dir=<path>]`: Source, resource and target dirs in the config are relative to the base directory. If not specified explicitly, this is the directory that contains the config file.
- `[-D<option>=<value>...]`: Configuration options can be overridden with `-D` arguments or through system properties.
- `<config-file>`: The path to the site config file, typically named `ornate.conf`.

All further configuration is done through the config file.

Ornate also provides an sbt plugin. In order to use it, you have to add it to your `project/plugins.sbt`:

```scala expandVars=true
addSbtPlugin("com.novocode" % "sbt-ornate" % "{{version}}")
```

A minimum project definition in `build.sbt` which enables the plugin looks like this:

```scala src=../../plugin/src/sbt-test/ornate/simple/build.sbt
```

You can run Ornate from sbt with the `ornate` task. By default it looks for a config file `src/ornate.conf`, source and resources under `src/site`, and it builds the site to `target/site`. These settings can be changed directly through the sbt configuration:

```scala src=../../plugin/src/main/scala/com/novocode/ornate/sbtplugin/OrnatePlugin.scala#--doc-plugin
```

> {.note}
> Note: At the moment you have to build Ornate form source. Published versions on Maven Central will come soon!

## Terminology

- **Page**: A page comes from a single CommonMark source file. It has a source URI and a page URI in the `site:` namespace. Themes can also create synthetic pages which are not associated with any source file.

- **Resource**: A file which is copied verbatim to the generated site. Resources share the `site:` namespace with pages. They can be provided as part of sources (together with pages) or be generated during processing.

- **TOC**: The table of contents is built from a global configuration and the sections of all pages.

- **Site**: The site consists of all pages and resources plus the computed TOC.

- **Extension**: An extension can be a parser and/or renderer extension for [commonmark-java](https://github.com/atlassian/commonmark-java) or an Ornate extension for page processing. Extensions can be enabled separately for each page.

- **Theme**: A theme is a class that renders the site in some way. The default themes generates HTML files and copies resources but themes are free to do whatever they want with the site.
