package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class Provider(
  providerId: Option[Long] = None,
  providerOwnerId: Option[Long],
  extId: String,
  name: String,
  `type`: String,
  location: Option[String],
) extends Product with Serializable

object Provider extends JsonConfig {
  implicit val format: OFormat[Provider] = Json.format[Provider]
  implicit val getResult: GetResult[Provider] = GenGetResult[Provider]
}
