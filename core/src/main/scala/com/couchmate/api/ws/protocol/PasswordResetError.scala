package com.couchmate.api.ws.protocol

import enumeratum._
import play.api.libs.json.{Format, Json}

sealed trait PasswordResetErrorCause extends EnumEntry

object PasswordResetErrorCause
  extends Enum[PasswordResetErrorCause]
  with PlayJsonEnum[PasswordResetErrorCause] {
  val values = findValues

  case object BadPassword extends PasswordResetErrorCause
  case object Unknown     extends PasswordResetErrorCause
}

case class PasswordResetError(
  cause: PasswordResetErrorCause
) extends Throwable

object PasswordResetError {
  implicit val format: Format[PasswordResetError] = Json.format[PasswordResetError]
}