package com.couchmate.api.ws.protocol

import enumeratum._
import play.api.libs.json.{Format, Json}

sealed trait LoginErrorCause extends EnumEntry

object LoginErrorCause
  extends Enum[LoginErrorCause]
  with PlayJsonEnum[LoginErrorCause] {
  val values = findValues

  case object NotVerified     extends LoginErrorCause
  case object BadCredentials  extends LoginErrorCause
  case object Unknown         extends LoginErrorCause
}

case class LoginError(
  cause: LoginErrorCause
) extends Throwable

object LoginError {
  implicit val format: Format[LoginError] = Json.format[LoginError]
}