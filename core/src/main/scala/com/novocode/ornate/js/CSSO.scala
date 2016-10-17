package com.novocode.ornate.js

import com.novocode.ornate.Logging

object CSSO extends Logging { self =>
  private val nashorn: NashornSupport = new NashornSupport { def logger = self.logger }
  private val csso = nashorn.mainModule.require("csso")

  def minify(s: String): String = {
    val res = nashorn.call[JSMap](csso, "minify", s)
    res.apply[String]("css")
  }
}
