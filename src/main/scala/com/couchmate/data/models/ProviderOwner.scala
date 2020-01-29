package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class ProviderOwner(
  providerOwnerId: Option[Long] = None,
  extProviderOwnerId: Option[String] = None,
  name: String,
) extends Product with Serializable

object ProviderOwner extends JsonConfig {
  implicit val format: OFormat[ProviderOwner] = Json.format[ProviderOwner]
  implicit val getResult: GetResult[ProviderOwner] = GenGetResult[ProviderOwner]
}
