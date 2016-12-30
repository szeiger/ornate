# URIs

URIs are used pervasively throughout Ornate for referencing different kinds of content. The following schemes (including several custom schemes) are supported:

## `site`

All pages and resources have a `site` URI. For example, if the source directory is `./doc/src`, the page located at `./doc/src/foo/bar.md` gets the `site` URI `site:/foo/bar.md`. All links and images on a page are resolved against the page's `site` URI. This makes it easy to use relative links that are identical to the corresponding `file` source URI links (for compatibility with other Markdown processors) or, alternatively, to use absolute links on deeply nested sites.

When a `site` URI link has no link text, the title of the link target (page or section) is used as the link text.

> {.note}
> Note that rendered sites always use relative links for pages and resources. They can be placed anywhere in a filesystem or on an HTTP server.

If `global.allowTargetLinks` is set to `true`, links may also use the target suffix instead of the source suffix (e.g. `foo.html` instead of `foo.md`). This option is provided for compatibility with other site generator tools. It is turned off by default.

## `webjar`, `theme`, `classpath`

These schemes reference resources on the classpath. They differ only in the lookup location. `classpath:/` points to the classpath root, `theme:/` points to the package containing the theme class and `webjar:/name/path` points to a path in the named webjar. They can be used for referencing CSS, JS and image resources in themes and image markup elements. These files are copied into the generated site as resources.

## `abs`

This is a special prefix for using arbitrary link destinations for links and images. The `abs:` prefix is stripped off and the remainder inserted verbatim into the generated site. This can be used to link to resources outside of the generated site. For example, `abs:/` links to the root of whatever filesystem or HTTP server the generated site is placed into. It is generally advisable to use `https:` links in these cases.

## `unchecked`

This is a special prefix that can be used with relative or absolute `site` URIs in links and image elements for referencing resources that are expected to be in the site's target location but are not present at build time. Links to `<path>` and `unchecked:<path>` are resolved in exactly the same way, except that the latter does not emit an error message when the resource is not found.

## `file`

Standard `file` URIs. They can be used for referencing CSS, JS and image resources in themes and image markup elements. These files are copied into the generated site as resources.

## `toctree` and `config`

These schemes are used to include TOCs and config values in a document. See [](images.md) for more details.
