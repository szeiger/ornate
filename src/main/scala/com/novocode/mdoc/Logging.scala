package com.novocode.mdoc

import org.slf4j.{Logger, LoggerFactory}

trait Logging {
  val logger: Logger = {
    val n = getClass.getName
    val n2 = if(n.endsWith("$")) n.dropRight(1) else n
    LoggerFactory.getLogger(n2)
  }
}
