package com.novocode.ornate.js

import scala.collection.JavaConverters._

import jdk.nashorn.api.scripting.JSObject

trait JSResultConverter[T] extends (AnyRef => T)
trait JSMap {
  def apply[T : JSResultConverter](key: String): T
}

object JSResultConverter {
  def apply[T](f: AnyRef => T): JSResultConverter[T] = new JSResultConverter[T] {
    def apply(v: AnyRef): T = f(v)
  }
  implicit val jsResultUnit: JSResultConverter[Unit] = JSResultConverter(_ => ())
  implicit val jsResultString: JSResultConverter[String] = JSResultConverter[String](o => if(o eq null) null else o.toString)
  implicit def jsResultVector[T](implicit ev: JSResultConverter[T]): JSResultConverter[Vector[T]] =
    JSResultConverter[Vector[T]](_.asInstanceOf[JSObject].values.iterator().asScala.map(ev).toVector)
  implicit val jsResultMap: JSResultConverter[JSMap] = JSResultConverter[JSMap] { o =>
    val jo = o.asInstanceOf[JSObject]
    new JSMap {
      def apply[R](key: String)(implicit ev: JSResultConverter[R]): R =
        if(jo.hasMember(key)) ev(jo.getMember(key)) else null.asInstanceOf[R]
      override def toString: String = {
        jo.keySet.iterator.asScala.map(k => s"$k: ${jo.getMember(k)}").mkString("{", ", ", "}")
      }
    }
  }
}
