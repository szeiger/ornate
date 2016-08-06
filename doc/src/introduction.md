---
#extensions = ${extensions} [ foo, -tables ]
#title: Foo
highlight.highlightjs.inline = scala
---
## Introduction at 2nd level

Some text here

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

Target text
