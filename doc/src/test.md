---
#extensions = ${extensions} [ foo, -tables ]
#title: Foo
highlight.highlightjs.inline = scala
---
# Target

## 2nd level

## Code snippet

Here's some Scala code:

```scala
  class AttributedHeading extends Heading {
    var id: String = null
    val attrs: ListBuffer[String] = new ListBuffer

    override protected def toStringAttributes = {
      val b = new StringBuilder().append(s"level=$getLevel")
      if(id ne null) b.append(" id="+id)
      if(attrs.nonEmpty) b.append(attrs.mkString(" attrs=[", ", ", "]"))
      b.toString
    }
  }
```

And an indented block:

    class Foo {
    }

And this `val x = 42` is inline code.

Here's an image from highlight.js:

![](webjar:/highlight.js/styles/brown-papersq.png)

Title     {#title_id foo=bar}
-----

#### Level 4

#### Level 4

### Level 3

### Level 3

Subtitle
--------

## toctree:maxLevel=2,mergeFirst=false {#contents_id} ##

![](toctree:maxLevel=2,mergeFirst=false)

## toctree: {#contents_id} ##

![foo](toctree: "bar")

## Pages

![](toctree:maxlevel=0,mergefirst=false)

End of document
