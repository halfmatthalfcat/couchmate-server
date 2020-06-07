package com.couchmate.data.models

import com.neovisionaries.i18n.CountryCode
import play.api.libs.json.{Format, Json}

case class ZipProvider(
  zipCode: String,
  countryCode: CountryCode,
  providerId: Long,
)

object ZipProvider extends JsonConfig {
  implicit val format: Format[ZipProvider] = Json.format[ZipProvider]
}
