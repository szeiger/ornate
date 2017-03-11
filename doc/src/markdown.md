---
extensions: ${extensions} [subscript, superscript]
---
# Markdown Extensions

The following extensions are provided out of the box.

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-extension-aliases
```

Not all extensions are enabled by default. This can be configured on a per-page basis through the `extensions` setting. The following is the default configuration:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-extensions
```

HOCON does not allow the removal of list elements when overriding a setting. (You can only replace the entire list or add elements at the beginning or end of it.) For `global.extensions` Ornate supports the use of a minus (`-`) prefix to remove existing elements. 

The order in which extensions are added generally does not matter. Extensions perform their processing in predefined phases to automatically handle dependencies correctly (e.g. first include external code snippets, then expand variables in them, then perform highlighting).

## commonmark-java extensions

See the [commonmark-java documentation](https://github.com/atlassian/commonmark-java#extensions) for `autolink`, `ins`, `strikethrough` and `tables`.

## `subscript` and `superscript`

These extensions enable subscript and superscript notation with the same syntax as [Pandoc](http://pandoc.org/MANUAL.html#superscripts-and-subscripts). Superscripts are enclosed by a single `^` characer, subscripts by a single `~` character. The enclosed text may not contain other markup. Enclosed spaces have to be escaped with backslashes.

Example source:

```markdown
H~2~O is a liquid. 2^10^ is 1024.
```

This gets rendered as:

H~2~O is a liquid. 2^10^ is 1024.

## `headerAttributes` and `autoIdentifiers` {#header_attributes}

The `headerAttributes` extension implements the same header attribute syntax as [Pandoc](http://pandoc.org/MANUAL.html#header-identifiers) and [PHP Markdown Extra](https://michelf.ca/projects/php-markdown/extra/). The IDs are essential for controlling header links. Every heading to which you want to link from other parts of the document, from the TOC, or from external sources, requires an ID. In case of the TOC, headings without an ID will still be listed but not linked.

Here is an example for a heading title with an ID:

```markdown
## All Configuration Settings {#settings}

Links to this section get to use `#settings` instead of
`#all_configuration_settings` (the auto-generated ID).
```

> {.note}
> Note: IDs starting with an underscore (`_`) character should be avoided for headers. The [default theme](default-theme.md) creates some IDs of this form for internal use.

The `autoIdentifiers` extension uses the same algorithm as Pandoc to automatically derive an ID from the heading title if no ID was set explicitly via `headerAttributes`

Other parts of the header attributes may be used by by other extensions or features. In particular, key/value pairs with an `index` key are used to define [index entries](default-theme.md#index).

## `blockQuoteAttributes`

This extension implements the same header attribute syntax for block quotes. Ornate recogizes the classes `.note` and `.warning` in block quote attributes to generate appropriately styled note and warning blocks. The header attributes must be the only thing on the first line of the block quote. Content starts on the second line.

Example source:

```markdown
> This is a regular block quote.

> {.note}
> This is a note.

> {.warning}
> This is a warning.
```

This gets rendered as:

> This is a reqular block quote.

> {.note}
> This is a note.

> {.warning}
> This is a warning.

## `mergeTabs`

This extension allows you to merge directly adjacent fenced code blocks into a tabbed view. This is controlled through the *info string* of the fenced code block. The CommonMark specification leaves interpretation of this string undefined, suggesting only that the first token define the highlighting language. Ornate parses the info string with the same syntax as [header attributes](#header_attributes). Adjacent fenced code blocks are merged if their info string contains a key/value pair with the key `tab`. The value is used as the tab title.

Example source:

````markdown
```scala tab=Scala
class AttributedHeading extends Heading {
  // ...
}
```

```java tab="Java Version"
public class AttributedHeading extends Heading {
  // ...
}
```
````

This gets rendered as:

```scala tab=Scala
class AttributedHeading extends Heading {
  // ...
}
```

```java tab="Java Version"
public class AttributedHeading extends Heading {
  // ...
}
```

## `includeCode`

This extension allows you to include code snippets from an external file in a fenced code block. The `src` attribute specifies the external file relative to the source URI of the current page. If the URI has a fragment, it is used to extract only parts of the file delimited by lines ending with the fragment ID (including the `#` symbol). The delimiter lines are not included, only the lines between them. Multiple delimited sections are allowed. They are concatenated when extrating the snippet. Each section is dedented individually by stripping off leading whitespace that is common to all lines (*including* the delimiter lines).

If the fenced code block is not empty, its original content is discarded. It can be used to show a placeholder in Markdown processors without this `includeCode` feature.

Example source:

````markdown
```scala src=../../core/src/main/scala/com/novocode/ornate/Main.scala#main
  Snippet Placeholder
```
````

This gets rendered as:

```scala src=../../core/src/main/scala/com/novocode/ornate/Main.scala#main
  Snippet Placeholder
```

`includeCode` can be configured to remove certain lines (matching a regular expression). This is useful for removing delimiter lines in case of overlapping delimited sections. The default configuration removes lines containing `//#` with nothing but whitespace in front from `.java`, `.scala` and `.sbt` snippets:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-includeCode
```

Links to the snippet sources can be generated automatically if a mapping is defined. For example, this manual has the following configuration under `extension.includeCode`:

```yaml src=../../doc/ornate.conf#--doc-sourceLinks
```

Each `sourceLinks` entry maps a `file:` URI (relative to `global.sourceDir`) denoting a base directory under which snippets referenced from the pages can be found to a base URI for the link destination. All snippets from the configured source directory get a corresponding `sourceLinkURI` attribute (unless one was specified manually). It is up to the [theme](default-theme.md#snippet-source-links) to make use of it.

If the link destination has the special fragment `#ghLines`, github-style line highlighting fragments are appended to the generated URIs for snippets which include only parts of a file. Note that github can only highlight one continuous region. If the snippet consists of multiple blocks, the first line of the first block and the last line of the last block are used.

## `renderMarkdown`

This extension parses Markdown code contained in a fenced code block and includes it in the document, replacing the fenced code block. All fenced code blocks with the language code (i.e. the first token in the info string) `markdown` plus an extra `render` token are processed. 

Example source:

````markdown
```markdown render
> This is a blockquote with *standard* **markup**.
```
````

This gets rendered as:

```markdown render
> This is a blockquote with *standard* **markup**.
```

This extension is not very useful on its own but you can combine it with [includeCode](#includecode) to include Markdown snippets from external files into a page, for example:

````markdown
```markdown render src=../../generated-content.md
```
````

## `expandVars`

This extension allows expansion of variables that reference configuration keys. The delimiters for variables are configurable, the default style being `{{variable}}`. Variable substutions are performed *after* Markdown parsing, so there is no way to escape delimiters. Global expansion for different node types can also be enabled in the configuration. By default this extension is enabled but all expansion options are disabled, so expansion is only performed in fenced code blocks with an explicit `expandVars=true` attribute. This is the default configuration:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-expandVars
```

Example source:

````markdown
```yaml expandVars=true
# Set the version to {{version}}:
version = "{{version}}"
```
````

This gets rendered as:

```yaml expandVars=true
# Set the version to {{version}}:
version = "{{version}}"
```

In plain text you can also use objects with [config](images.md#config) URIs instead of `expandVars` but this is not possible in code elements, which cannot contain embedded Markdown syntax.

## `emoji` {#emoji}

The `emoji` extension translates Emoji names to the appropriate Unicode representations or images.

Example source:

```markdown
Is this feature :thumbsup: or :thumbsdown:?
```

This gets rendered as:

Is this feature :thumbsup: or :thumbsdown:?

The format can be changed in the configuration:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-emoji
```

## `smartQuotes`

This extension converts single (`'`) and double (`"`) ASCII quotes to balanced Unicode quotes (`‘single’` and `“double”`) in all text content (including link and image titles). Code sections are not processed.

Example source:

```markdown
> "Hello," said the spider.  "'Shelob' is my name."
> 
> 'A', 'B', and 'C' are letters.
> 
> 'Oak,' 'elm,' and 'beech' are names of trees.
> So is 'pine.'
> 
> 'He said, "I want to go."'  Were you alive in the
> 70's?
> 
> Here is some quoted '`code`' and a "[quoted link](#smartquotes)".
```

This gets rendered as:

> "Hello," said the spider.  "'Shelob' is my name."
> 
> 'A', 'B', and 'C' are letters.
> 
> 'Oak,' 'elm,' and 'beech' are names of trees.
> So is 'pine.'
> 
> 'He said, "I want to go."'  Were you alive in the
> 70's?
> 
> Here is some quoted '`code`' and a "[quoted link](#smartquotes)".

## `smartPunctuation`

This extension converts the ASCII replacements for em-dashes (`---`), en-dashes (`--`) and ellipses (`...`) to the proper Unicode glyphs `—`, `–` and `…`) in all text content (including link and image titles). Code sections are not processed.

Example source:

```markdown
> Some dashes:  one---two --- three---four --- five.
>
> Dashes between numbers: 5--7, 255--66, 1987--1999. 
>
> Ellipses...and...and....
```

This gets rendered as:

> Some dashes:  one---two --- three---four --- five.
>
> Dashes between numbers: 5--7, 255--66, 1987--1999. 
>
> Ellipses...and...and....

## `globalRefs`

This extension allows you to prepend reference targets that are defined in the site config or page config to every page. This is useful for targets that are either computed from other config values or used on many pages. This is the default configuration:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-globalRefs
```

The keys in the map are the reference labels. The values are defined in the same way as [TOC entries](configuration.md#table-of-contents), either as a string (containing the link target) or an object with the fields `url` and `title`.

For example, this manual uses for following global refs:

```yaml src=../../doc/ornate.conf#--doc-globalRefs
```

## `scaladocLinks`

This extension simplifies linking to scaladoc entries by adding custom URI schemes that correspond to scaladoc sets. The extension is enabled by default but no schemes are configured. This manual uses the following configuration:

```yaml src=../../doc/ornate.conf#--doc-scaladocLinks
```

Each key in the configuration (in this case only one, `api`) adds a URI scheme. The value is a config object with an `index` setting that defines the URI of the `index.html` file produced by scaladoc.

> {.note}
> Note the use of the [`unchecked` prefix](uris.md#unchecked) to suppress the errors that would otherwise be generated when building the manual without the scaladocs already in place. This is recommended for scaladoc links because the standard link checking is useless for scaladocs (use [sdlc](https://github.com/typesafehub/sbt-sdlc) instead) and you may want to quickly build the manual without building the scaladocs first (which can be slow for large projects).

Example source:

```text
- [named link](api:com.novocode.ornate.highlight.Highlighter)
- [](api:com.novocode.ornate.highlight.Highlighter)
- <api:com.novocode.ornate.highlight.Highlighter>
- <api:com.novocode.ornate.Extension@preProcessors(pageConfig:com.typesafe.config.Config):Seq[com.novocode.ornate.PreProcessor]>
```

This gets rendered as:

- [named link](api:com.novocode.ornate.highlight.Highlighter)
- [](api:com.novocode.ornate.highlight.Highlighter)
- <api:com.novocode.ornate.highlight.Highlighter>
- <api:com.novocode.ornate.Extension@preProcessors(pageConfig:com.typesafe.config.Config):Seq[com.novocode.ornate.PreProcessor]>

When the link text is left empty or is identical to the link target (which is the case when using the `<scheme:scheme-specific-part>` link syntax), it is automatically derived from the link target.

## `externalLinks`

Similar to [`scaladocLinks`](#scaladoclinks) this extension processes custom URI schemes in links but it has configurable patterns for link targets and link texts. The extension is enabled by default but no schemes are configured. This manual uses the following configuration to link to Ornate issues on github:

```yaml src=../../doc/ornate.conf#--doc-externalLinks
```

Each key in the configuration adds a URI scheme. The value is a config object with a `uri` setting that defines the pattern for the link target and an optional `text` setting that defines a pattern for the link text. Link texts are only generated if `text` is defined and a link does not already have a text. All occurences of `[all]` in the patterns are replaced by the scheme-specific part of the link target.

Example source:

```text
- [Issue 2](issue:2)
- [](issue:2)
```

This gets rendered as:

- [Issue 2](issue:2)
- [](issue:2)

## `mathSyntax`

This extensions enables common notation for inline and display (block) math. See [mathSyntax extension](math.md#mathSyntax) for details.

## `highlightjs`

See [](highlighting.md) for details.
