package com.couchmate.api.ws.protocol

import com.couchmate.api.models.grid.Grid
import com.couchmate.api.models.{Provider, User}
import com.couchmate.data.models.CountryCode
import julienrf.json.derived
import play.api.libs.json.{Format, __}

sealed trait Protocol

object Protocol {
  implicit val format: Format[Protocol] =
    derived.flat.oformat((__ \ "type").format[String])
}

/**
 * Unauthenticated Messages
 */

case class ValidateNewAccount(email: String, username: String) extends Protocol
case class ValidateNewAccountResponse(
  status: ValidateNewAccountResponseStatus
) extends Protocol

case class CreateNewAccount(
  email: String,
  username: String,
  password: String,
  zipCode: String,
  providerId: Long,
) extends Protocol
case class CreateNewAccountSuccess(
  user: User
) extends Protocol
case class CreateNewAccountFailure(
  cause: CreateNewAccountError
) extends Protocol

case class GetProviders(
  zipCode: String,
  country: CountryCode
) extends Protocol
case class GetProvidersResponse(
  providers: Seq[Provider]
) extends Protocol

case class GetGrid(
  providerId: Long
) extends Protocol
case class GetGridResponse(
  grid: Grid
) extends Protocol

case class AppendMessage(message: String) extends Protocol