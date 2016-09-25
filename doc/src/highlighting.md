# Code Highlighting

A highlighter is a class that implements the `Highlighter` trait. It can be set in the site configuration in `global.highlight`.

The default highlighter runs [highlight.js](https://highlightjs.org/) at build time in Nashorn. All languages from the highlight.js distribution are supported but only a few of them are enabled by default to improve startup times. This and other settings can be changed in the page or site configuration. These are the defaults:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-highlightjs
```

Note that the default theme overrides any background color set by the highlight.js style.
