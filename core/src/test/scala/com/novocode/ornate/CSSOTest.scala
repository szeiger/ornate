package com.novocode.ornate

import com.novocode.ornate.js.CSSO
import org.junit.Test
import org.junit.Assert._

class CSSOTest {
  @Test def testWrapped: Unit = {
    val min = CSSO.minify(".test { color: #ff0000; }")
    assertEquals(".test{color:red}", min)
  }
}
