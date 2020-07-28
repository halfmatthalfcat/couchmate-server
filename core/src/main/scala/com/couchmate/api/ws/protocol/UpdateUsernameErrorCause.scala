package com.couchmate.api.ws.protocol

import enumeratum._
import play.api.libs.json.{Format, Json}

sealed trait UpdateUsernameErrorCause extends EnumEntry

object UpdateUsernameErrorCause
  extends Enum[UpdateUsernameErrorCause]
  with PlayJsonEnum[UpdateUsernameErrorCause] {
  val values = findValues

  case object InvalidUsername       extends UpdateUsernameErrorCause
  case object UsernameExists        extends UpdateUsernameErrorCause
  case object AccountNotRegistered  extends UpdateUsernameErrorCause
  case object Unknown               extends UpdateUsernameErrorCause
}

case class UpdateUsernameError(
  cause: UpdateUsernameErrorCause
) extends Throwable

object UpdateUsernameError {
  implicit val format: Format[UpdateUsernameError] = Json.format[UpdateUsernameError]
}