package com.novocode.mdoc

import org.junit.Test
import org.junit.Assert._

class FileMatcherTest {
  case class F(name: String, children: F*) {
    var parent: F = _
    children.foreach(_.parent = this)
    override def toString =
      if(parent eq null) name else parent.toString + '/' + name
  }

  val f1 =
    F("r",
      F("doc",
        F("src",
          F("index.md"),
          F("introduction.md")
        ),
        F("target",
          F("index.html"),
          F("introduction.html")
        ),
        F("mdoc.conf")
      ),
      F("src",
        F("main",
          F("logback.xml"),
          F("mdoc-reference.conf")
        ),
        F("doc")
      )
    )

  def check(f: F, gitignore: String, exp: Vector[String]): Unit = {
    val m = new FileMatcher(gitignore)
    //m.entries.foreach(println)
    val res = m.filterAny(f)(_.name)(_.children.nonEmpty)(_.children.toVector)
    //res.foreach(f => println("      \""+f+"\","))
    assertEquals(exp, res.map(_.toString))
  }

  @Test def test1: Unit = check(f1, "", Vector(
      "r/doc",
      "r/doc/src",
      "r/doc/src/index.md",
      "r/doc/src/introduction.md",
      "r/doc/target",
      "r/doc/target/index.html",
      "r/doc/target/introduction.html",
      "r/doc/mdoc.conf",
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/main/mdoc-reference.conf",
      "r/src/doc"
    ))

  @Test def test2: Unit = check(f1,
    """
      |i*
      |!ind
    """.stripMargin, Vector(
      "r/doc",
      "r/doc/src",
      "r/doc/target",
      "r/doc/mdoc.conf",
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/main/mdoc-reference.conf",
      "r/src/doc"
    ))

  @Test def test3: Unit = check(f1,
    """
      |*i*
      |!ind*
    """.stripMargin, Vector(
      "r/doc",
      "r/doc/src",
      "r/doc/src/index.md",
      "r/doc/target",
      "r/doc/target/index.html",
      "r/doc/mdoc.conf",
      "r/src",
      "r/src/doc"
    ))


  @Test def test4: Unit = check(f1,
    """
      |*i*
      |!ind*
      |target/i*
    """.stripMargin, Vector(
      "r/doc",
      "r/doc/src",
      "r/doc/src/index.md",
      "r/doc/target",
      "r/doc/mdoc.conf",
      "r/src",
      "r/src/doc"
    ))

  @Test def test5: Unit = check(f1,
    """doc
      |
    """.stripMargin, Vector(
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/main/mdoc-reference.conf"
    ))

  @Test def test6: Unit = check(f1,
    """
      |/doc
    """.stripMargin, Vector(
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/main/mdoc-reference.conf",
      "r/src/doc"
    ))

  @Test def test7: Unit = check(f1,
    """# match doc dir
      |doc/
    """.stripMargin, Vector(
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/main/mdoc-reference.conf",
      "r/src/doc"
    ))

  @Test def test8: Unit = check(f1, "/doc/**/*md*  ", Vector(
      "r/doc",
      "r/doc/src",
      "r/doc/target",
      "r/doc/target/index.html",
      "r/doc/target/introduction.html",
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/main/mdoc-reference.conf",
      "r/src/doc"
    ))

  @Test def test9: Unit = check(f1, "main/m*", Vector(
      "r/doc",
      "r/doc/src",
      "r/doc/src/index.md",
      "r/doc/src/introduction.md",
      "r/doc/target",
      "r/doc/target/index.html",
      "r/doc/target/introduction.html",
      "r/doc/mdoc.conf",
      "r/src",
      "r/src/main",
      "r/src/main/logback.xml",
      "r/src/doc"
    ))
}
