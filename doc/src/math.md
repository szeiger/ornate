# Math

The [default theme](default-theme.md) can embed [MathJax](https://www.mathjax.org/) to render math notation. It recognizes fenced code blocks whose info string starts with the language codes `texmath`, `asciimath` and `mathml` and treats them specially instead of calling the standard [highligher](highlighting.md).

> {.note}
> Note that the actual math rendering is done on the client side with JavaScript.

Example:

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

MathJax config files and options for a `text/x-mathjax-config` script can be specified in the theme's global configuration (`theme.default.global` for the default theme):

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-mathjax
```
