package com.couchmate.api.ws.protocol

import java.util.UUID

import com.couchmate.api.models.grid.Grid
import com.couchmate.api.models.{Provider, User}
import com.couchmate.data.models.{UserMeta, UserRole}
import com.couchmate.util.json.CountryCodePlayJson
import com.neovisionaries.i18n.CountryCode
import julienrf.json.derived
import play.api.libs.json.{Format, __}

sealed trait Protocol

object Protocol extends CountryCodePlayJson {
  implicit val format: Format[Protocol] =
    derived.flat.oformat((__ \ "type").format[String])
}

case class InitSession(
  timezone: String,
  locale: String
) extends Protocol
case class Login(
  email: String,
  password: String
) extends Protocol
case class RestoreSession(
  token: String,
  roomId: Option[UUID]
) extends Protocol
case class SetSession(
  user: User,
  provider: String,
  token: String
) extends Protocol

case class ValidateNewAccount(
  email: String,
  username: String,
) extends Protocol
case class ValidateNewAccountResponse(
  status: ValidateAccountStatus
) extends Protocol

case class RegisterAccount(
  email: String,
  username: String,
) extends Protocol
case class RegisterAccountResponse(
  meta: UserMeta
) extends Protocol
case class RegisterAccountFailure(
  cause: RegisterAccountError
) extends Protocol

case class GetProviders(
  zipCode: String,
  country: CountryCode
) extends Protocol
case class GetProvidersResponse(
  providers: Seq[Provider]
) extends Protocol

case class UpdateGrid(
  grid: Grid
) extends Protocol


case class AppendMessage(message: String) extends Protocol