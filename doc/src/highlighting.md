# Code Highlighting

A highlighter is a class that implements the `Highlighter` trait. It can be set in the site configuration in `global.highlight`.

The default highlighter runs [highlight.js](https://highlightjs.org/) at build time in Nashorn. All languages from the highlight.js distribution are supported but only a few of them are enabled by default to improve startup times. This and other settings can be changed in the page or site configuration. These are the defaults:

```yaml
# Settings for the highlight.js-based syntax highlighter
highlight.highlightjs {
  # Preloaded languages. Any language that you want to access via an alias should be listed here.
  preload = [scala, java, json, yaml, sql, ini, diff, bash, xml, markdown]

  # Default language when none is specified. Can be one of the supported languages, an array
  # of languages from which to pick automatically, or null or an empty list for plain text.
  # There are separate defaults for fenced code blocks, indented code blocks and inline code.
  fenced    = ${highlight.highlightjs.preload}
  indented  = ${highlight.highlightjs.preload}
  inline    = null

  # URIs of CSS files and assorted resources required for the style (relative to webjar:/highlight.js/styles/)
  styleResources = [ github-gist.css ]
}
```

Note that the default theme overrides any background color set by the highlight.js style.
