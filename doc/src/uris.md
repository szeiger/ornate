# URIs

URIs are used pervasively throughout Ornate for referencing different kinds of content. The following schemes (including several custom schemes) are supported:

## site

All pages and resources have a `site` URI. For example, if the source directory is `./doc/src`, the page located at `./doc/src/foo/bar.md` gets the `site` URI `site:/foo/bar.md`. All links and images on a page are resolved against the page's `site` URI. This makes it easy to use relative links that are identical to the corresponding `file` source URI links (for compatibility with other Markdown processors) or, alternatively, to use absolute links on deeply nested sites.

> {.note}
> Note that rendered sites always use relative links for pages and resources. They can be placed anywhere in a filesystem or on an HTTP server.

## webjar, theme, classpath

These protocols reference resources on the classpath. They differ only in the lookup location. `classpath:/` points to the classpath root, `theme:/` points to the package containing the theme class and `webjar:/name/path` points to a path in the named webjar. They can be used for referencing CSS, JS and image resources in themes and image markup elements. These files are copied into the generated site as resources.

## abs

This is a special prefix for using arbitrary link destinations for links and images. The `abs:` prefix is stripped off and the remainder inserted verbatim into the generated site. This can be used to link to resources outside of the generated site. For example, `abs:/` links to the root of whatever filesystem or HTTP server the generated site is placed into. It is generally advisable to use `https:` links in these cases.

## file

Standard `file` URIs. They can be used for referencing CSS, JS and image resources in themes and image markup elements. These files are copied into the generated site as resources.

## toctree

`toctree` URIs represent the generated Table of Contents. They can be used in image elements or parts of the theme configuration. URIs are of the form `toctree:key=value,key=value,...` with case-insentive keys. The following keys are supported:

- `maxLevel`: The maximum nesting level to show in the TOC. Pages have level 0, heading elements have levels 1 to 6. When unset, this defaults to the configuration setting `global.toxMaxLevel`.
- `mergeFirst`: Whether to merge the page and the first heading (`true` or `false`). Usually pages have a single level-1 heading at the top and no separate title, so it does not make sense to show them in the TOC. With `mergeFirst=true` the first heading is shown as the title but the link goes to the page. When unset, this defaults to the configuration setting `global.toxMergeFirst`.
- `local`: Whether to include only TOC entries from the current page. Defaults to `false`.
- `focusMaxLevel`: A separate maximum nesting level for the current page. When unset, this is the same as `maxLevel`.
