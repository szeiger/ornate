# Default Theme

Ornate's default theme generates multiple HTML pages for the site, one per page. It uses [Foundation](http://foundation.zurb.com/)'s Flexbox-based CSS layout for responsive design across different classes of devices. All assets are included in the generated site by default.

## Configuration

This is the theme's default configuration:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-theme
```

## Search

The default theme can generate a JavaScript-based search function for the site using [Elasticlunr](http://elasticlunr.com/). The search index includes page titles, content and keywords. See above for configuration options.
