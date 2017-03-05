package com.novocode.ornate

import com.novocode.ornate.js.ElasticlunrSearch
import com.novocode.ornate.js.JSMap
import com.novocode.ornate.js.NashornSupport
import org.junit.Test

class ElasticlunrTest {
  @Test def testBasic: Unit = {
    val nashorn: NashornSupport = new NashornSupport with Logging
    val el = nashorn.mainModule.require("elasticlunr")
    val index = nashorn.engine.invokeMethod(el, "call")
    nashorn.call[Unit](index, "addField", "title")
    nashorn.call[Unit](index, "addField", "body")
    nashorn.call[Unit](index, "saveDocument", false)
    val doc1 = nashorn.engine.createBindings()
    doc1.put("id", 1)
    doc1.put("title", "Oracle released its latest database Oracle 12g")
    doc1.put("body", "Yestaday Oracle has released its new database Oracle 12g, this would make more money for this company and lead to a nice profit report of annual year.")
    val doc2 = nashorn.engine.createBindings()
    doc2.put("id", 2)
    doc2.put("title", "Oracle released its profit report of 2015")
    doc2.put("body", "As expected, Oracle released its profit report of 2015, during the good sales of database and hardware, Oracle's profit of 2015 reached 12.5 Billion.")
    nashorn.call[Unit](index, "addDoc", doc1)
    nashorn.call[Unit](index, "addDoc", doc2)
    println(nashorn.stringifyJSON(index))
    val res = nashorn.call[Vector[JSMap]](index, "search", "Oracle database profit")
    println(res)
  }

  @Test def testWrapped: Unit = {
    val els = ElasticlunrSearch
    val idx = els.createIndex
    idx.add(
      "Oracle released its latest database Oracle 12g",
      "Yestaday Oracle has released its new database Oracle 12g, this would make more money for this company and lead to a nice profit report of annual year.",
      "Yestaday Oracle has released...",
      "",
      ""
    )
    idx.add(
      "Oracle released its profit report of 2015",
      "As expected, Oracle released its profit report of 2015, during the good sales of database and hardware, Oracle's profit of 2015 reached 12.5 Billion.",
      "As expected, Oracle released...",
      "",
      ""
    )
    println(idx.toJSON)
  }
}
