package com.couchmate.common.models.data

import enumeratum._
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
 * General Application Error
 */

case class CMError[T <: EnumEntry: Writes](
  reason: T,
  message: Option[String] = None,
) extends RuntimeException

object CMError {
  implicit def format[T <: EnumEntry: Writes](implicit format: Format[T]): Writes[CMError[T]] = (
    (__ \ "reason").write[T] and
    (__ \ "message").writeNullable[String]
  )(unlift(CMError.unapply[T]))
}