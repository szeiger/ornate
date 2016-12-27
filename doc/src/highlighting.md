# Code Highlighting

Code highlighting is provided through the `higlightjs` extension which runs [highlight.js](https://highlightjs.org/) at build time in Nashorn. All languages from the highlight.js distribution are supported but only a few of them are enabled by default to improve startup time. This and other settings can be changed in the page or site configuration. These are the defaults:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-highlightjs
```

> {.note}
> Note: The default theme overrides any background color set by the highlight.js style.

> {.note}
> Note: A highlighter extension should always be added *after* all other extensions that may modify the contents of code blocks and inline code. Once highlighting has been performed, any further modification to the code by other extensions has no effect.
