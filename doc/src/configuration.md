# Configuration

The configuration is written in HOCON. The site configuration is in `ornate.conf`. It is resolved against the reference configuration `ornate-reference.conf` that ships with ornate. Every page may have a HOCON front matter section at the top, for example:

    ---
    highlight.highlightjs.inline = scala
    ---
    # Page Title

    All `inline code` on this page gets Scala highlighting!

The page configuration consists of the front matter resolved against the site configuration, so you can override site configuration settings for a page, or even define new variables in the site configuration and reference them from a page configuration.

## Global Settings

The configuration path `global` contains settings that are used in a global context, i.e. they are always looked up in the site configuration and not in page configurations.

> {.note}
> *Note:* There are other paths for specific modules like `theme.default.global` which are treated the same. This behavior is only by convention. Configuration and lookup for these sections is the same as for any other part of the configuration but overriding settings in these sections in a page configuration would be fruitless because they are never used. Configuration settings outside of `global` sections are generally looked up in page configurations.

The most important global settings are:

```yaml
global {
  # Directory containing the document sources
  sourceDir = "src"
  # Directory containing static resources that should be copied to the target site
  resourceDir = ${global.sourceDir}
  # Directory to which the output is rendered
  targetDir = "target"
  # TOC structure or null for no TOC
  toc = null
  # The theme, which also determines the output format
  theme = default
}
```

## Table of Contents

`global.toc` is a list of pages. Is is necessary to list them explicitly so that they can be included in the desired order. Any page not listed in `global.toc` will still be processed as part of the site but it will not contribute TOC entries.

Here is a simple TOC:

```yaml
global.toc = [
  index.md
  configuration.md
  elements.md
]
```

TOC entries are either `site:` URIs (relative to `site:/`) or objects containing a URI and a title:

```yaml
  { title = "Test Page", url = "test.md" }
```

Setting a title in the TOC should rarely be necessary. If unset, the title defaults to the page title, which can be set in the front matter or default to the first heading on the page.
