package com.couchmate.data.models

import play.api.libs.json.{Json, Format}

case class ZipProvider(
  zipCode: String,
  countryCode: CountryCode,
  providerId: Long,
)

object ZipProvider extends JsonConfig {
  implicit val format: Format[ZipProvider] = Json.format[ZipProvider]
}
