package com.couchmate.data.models

import play.api.libs.json.{Format, Json}

case class ZipProviderDetailed(
  zipCode: String,
  countryCode: CountryCode,
  providerId: Long,
  name: String,
  `type`: String,
  location: Option[String]
)

object ZipProviderDetailed extends JsonConfig {
  implicit val format: Format[ZipProviderDetailed] = Json.format[ZipProviderDetailed]
}
