package com.couchmate.api.ws.protocol

import enumeratum._
import play.api.libs.json.{Format, Json}

sealed trait ForgotPasswordErrorCause extends EnumEntry

object ForgotPasswordErrorCause
  extends Enum[ForgotPasswordErrorCause]
  with PlayJsonEnum[ForgotPasswordErrorCause] {
  val values = findValues

  case object NoAccountExists extends ForgotPasswordErrorCause
  case object BadToken        extends ForgotPasswordErrorCause
  case object TokenExpired    extends ForgotPasswordErrorCause
  case object Unknown         extends ForgotPasswordErrorCause
}

case class ForgotPasswordError(
  cause: ForgotPasswordErrorCause
) extends Throwable

object ForgotPasswordError {
  implicit val format: Format[ForgotPasswordError] = Json.format[ForgotPasswordError]
}