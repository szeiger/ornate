package com.novocode.ornate

import java.util.concurrent.atomic.AtomicBoolean

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase

object ErrorRecognitionAppender {
  private[this] val flag = new AtomicBoolean(false)
  private def trigger() = flag.set(true)
  def rearm(): Boolean = flag.getAndSet(false)
}

class ErrorRecognitionAppender[E] extends UnsynchronizedAppenderBase[E] {
  def append(e: E): Unit = e match {
    case i: ILoggingEvent if i.getLevel.isGreaterOrEqual(Level.ERROR) => ErrorRecognitionAppender.trigger()
    case _ =>
  }
}
