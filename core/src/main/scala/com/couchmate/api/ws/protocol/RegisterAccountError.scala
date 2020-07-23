package com.couchmate.api.ws.protocol

import enumeratum._
import play.api.libs.json.{Format, Json}

sealed trait RegisterAccountErrorCause extends EnumEntry

object RegisterAccountErrorCause
  extends Enum[RegisterAccountErrorCause]
  with PlayJsonEnum[RegisterAccountErrorCause] {
  val values = findValues

  case object EmailExists     extends RegisterAccountErrorCause
  case object TokenExpired    extends RegisterAccountErrorCause
  case object BadToken        extends RegisterAccountErrorCause
  case object UserMismatch    extends RegisterAccountErrorCause
  case object UserNotFound    extends RegisterAccountErrorCause
  case object UnknownError    extends RegisterAccountErrorCause
}

case class RegisterAccountError(
  cause: RegisterAccountErrorCause
) extends Throwable

object RegisterAccountError {
  implicit val format: Format[RegisterAccountError] = Json.format[RegisterAccountError]
}