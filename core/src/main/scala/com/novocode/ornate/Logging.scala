package com.novocode.ornate

import org.slf4j.{Logger, LoggerFactory}

trait Logging {
  val logger: Logger = {
    val n = getClass.getName
    val n2 = if(n.endsWith("$")) n.dropRight(1) else n
    LoggerFactory.getLogger(n2)
  }

  final def logTime[T](msg: String)(f: => T): T = {
    if(!logger.isDebugEnabled) f
    else {
      val t0 = System.currentTimeMillis()
      val res = f
      logger.debug(msg + " " + (System.currentTimeMillis()-t0) + "ms")
      res
    }
  }
}
