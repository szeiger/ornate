package com.novocode.ornate.js

import com.novocode.ornate.Logging
import play.api.libs.json.{JsArray, JsObject, JsString}
import play.libs.Json

import scala.collection.mutable

object ElasticlunrSearch extends Logging { self =>
  private val nashorn: NashornSupport = new NashornSupport { def logger = self.logger }
  private val el = nashorn.mainModule.require("elasticlunr")

  def createIndex: Index = nashorn.synchronized {
    new Index {
      private val idx = nashorn.engine.invokeMethod(el, "call")
      private val data = new mutable.ArrayBuffer[(String, String, String)]
      nashorn.call[Unit](idx, "addField", "t")
      nashorn.call[Unit](idx, "addField", "b")
      nashorn.call[Unit](idx, "addField", "k")
      nashorn.call[Unit](idx, "saveDocument", false)
      private var currentId = 0

      def add(title: String, body: String, excerpt: String, keywords: String, link: String): Unit = nashorn.synchronized {
        val doc = nashorn.engine.createBindings()
        doc.put("id", currentId)
        doc.put("t", title)
        doc.put("b", body)
        doc.put("k", keywords)
        nashorn.call[Unit](idx, "addDoc", doc)
        data += ((title, excerpt, link))
        currentId += 1
      }

      def toJSON: String = nashorn.synchronized {
        val el = nashorn.stringifyJSON(idx)
        val ex = JsArray(data.map { case (title, ex, link) =>
          JsArray(Seq(JsString(title), JsString(ex), JsString(link)))
        }).toString()
        s"{idx:$el,data:$ex}"
      }
    }
  }

  trait Index {
    def add(title: String, body: String, excerpt: String, keywords: String, link: String): Unit
    def toJSON: String
  }
}
