*Ornate* is a tool for building multi-page HTML sites from Markdown sources. The design goals are:

- Runs on the JVM; No installation required: Resolve the versioned dependencies in your build process for reproducible documentation builds with no external dependencies.
- Based on [CommonMark](http://commonmark.org/), a standardized version of [Markdown](http://daringfireball.net/projects/markdown/).
- Use well-established extensions: In particular, many developers are already familiar with [Github-flavored Markdown](https://help.github.com/categories/writing-on-github/).
- Graceful degradation: Some features require proprietary syntax extensions. They should degrade gracefully when processed with a pure CommonMark engine.
- Configuration based on [Typesafe Config](https://github.com/typesafehub/config).
- Modular design: Themes, templates, highlighters, extensions - everything can be customized with Scala code.
- Clean, modern default theme using responsive design for a good user experience on all device from mobile phones to desktop PCs.

See [LICENSE.txt](LICENSE.txt) for licensing information.

Documentation can be found at <http://szeiger.github.io/ornate-doc/>
