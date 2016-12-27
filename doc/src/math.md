---
extension.mathSyntax {
  dollarInlineCode = asciimath
  dollarBlock = tex
}
---
# Math

The [default theme](default-theme.md) can use [MathJax](https://www.mathjax.org/) to render math notation. All required resources are embedded in the generated site but the rendering is done on the client side with JavaScript. Only a `[math]` placeholder will be visible if JavaScript is disabled.

MathJax config files and options for a `MathJax.Hub.Config` call can be specified in the theme's global configuration (under `theme.default.global` for the default theme):

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-mathjax
```

This default configuration disables preprocessing (which is not needed if you use Ornate's math syntax extensions) and preloads a config file with everything that is needed for rendering TeX, [AsciiMath](http://asciimath.org/) and MathML. Since Ornate only adds MathJax to pages that actually need it, the use of a `-full` config file is recommended. If you only need AsciiMath notation, you can use the smaller `AM_CHTML-full` configuration.

## Fenced Code Blocks

Fenced code blocks whose info string starts with the language codes `texmath`, `asciimath` (or simply `math`) and `mathml` are
treated as display math instead of passing them on to the standard [highlighter](highlighting.md).

TeX example:

````markdown
```texmath
\begin{array}{lcll}
\{a, A\} \;\vec{+}\; B &=& a, (A \;\vec{+}\; B)  &{\bf if} \; a \not\in B \\\\
                       &=& A \;\vec{+}\; B       &{\bf if} \; a \in B
\end{array}
```
````

This is rendered as:

```texmath
\begin{array}{lcll}
\{a, A\} \;\vec{+}\; B &=& a, (A \;\vec{+}\; B)  &{\bf if} \; a \not\in B \\\\
                       &=& A \;\vec{+}\; B       &{\bf if} \; a \in B
\end{array}
```

AsciiMath example:

````markdown
```math
sum_(i=1)^n i^3=((n(n+1))/2)^2
```
````

This is rendered as:

```math
sum_(i=1)^n i^3=((n(n+1))/2)^2
```

## `mathSyntax` extension {#mathSyntax}

The `mathSyntax` extension enables special syntax for inline and display (block) math. There is no single standard notation for [math in Markdown](https://github.com/cben/mathdown/wiki/math-in-markdown), therefore it can be customized to support several common notations:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-mathSyntax
```

> {.note}
> The `mathSyntax` extension is enabled by default but all notations are disabled.

- `dollarInline`: Standard TeX inline math notation, with the same syntax as [Pandoc's `tex_math_dollars` extension](http://pandoc.org/MANUAL.html#math): Anything between two `$` characters is treated as math. The opening `$` must have a non-space character immediately to its right, while the closing `$` must have a non-space character immediately to its left, and must not be followed immediately by a digit. If for some reason you need to enclose text in literal `$` characters, backslash-escape them and they won’t be treated as math delimiters. 

  Example:

  ```markdown
  Here is some inline math $a+b$ but neither is `$a+b$` nor $20,000 and $30,000, nor \$a+b\$.
  ```

  This is rendered as:

  Here is some inline math `$a+b$` but neither is `\$a+b$` nor $20,000 and $30,000, nor \$a+b\$.

  > {.note}
  > Note that the inline math notation is parsed before any other inline notation, in particular before inline code. This can have unwanted side effects when using the single dollar syntax. For example, when placing another inline code section `` `$a+b$` `` at the end of the previous paragraph, everything from `$20,000` to the first `$` of the inline code section would be recognized as inline math.

- `dollarBlock`: Standard TeX display math notation. Blocks delimited by `$$` are treated as display math.

  Example:

  ```markdown
  $$ \lim_{x \to \infty} \exp(-x) = 0 $$
  ```

  This is rendered as:

  $$ \lim_{x \to \infty} \exp(-x) = 0 $$

- `singleBackslash`: Standard LaTeX notation for `\(inline\)` and `\[display\]` math.

- `doubleBackslash`: MultiMarkdown's adaptation of LaTeX notation for `\\(inline\\)` and `\\[display\\]` math.

- `dollarInlineCode`: Inline code blocks with `$` characters at the beginning and end like `` `$a+b$` `` are parsed as inline math. The initial `$` can be escaped as `\$` to prevent math parsing.

- `dollarFenced`: Standard TeX inline math notation (like with `dollarInline`) in fenced code blocks. The usual [highlighting](highlighting.md) will be applied to such blocks. In order to minimize unwanted interaction of the highlighter with the unexpected math notation, inline math appears as a regular ASCII identifier to the highlighter. The real math notation is spliced back in after highlighting. This setting can be configured individually for each fenced code block with `dollarMath=tex|asciimath|null` in the info string.

  For example:

  ````markdown
  ```scala dollarMath=tex
  def copy[$\mathit{tps}\,$]($\mathit{ps}'_1\,$)$\ldots$($\mathit{ps}'_n$): $c$[$\mathit{tps}\,$] = new $c$[$\mathit{Ts}\,$]($\mathit{xs}_1\,$)$\ldots$($\mathit{xs}_n$)
  ```
  ````

  This is rendered as:

  ```scala dollarMath=tex
  def copy[$\mathit{tps}\,$]($\mathit{ps}'_1\,$)$\ldots$($\mathit{ps}'_n$): $c$[$\mathit{tps}\,$] = new $c$[$\mathit{Ts}\,$]($\mathit{xs}_1\,$)$\ldots$($\mathit{xs}_n$)
  ```
