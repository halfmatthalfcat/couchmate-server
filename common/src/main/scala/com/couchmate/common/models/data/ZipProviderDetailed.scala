package com.couchmate.common.models.data

import com.neovisionaries.i18n.CountryCode
import play.api.libs.json.{Format, Json}

case class ZipProviderDetailed(
  zipCode: String,
  countryCode: CountryCode,
  providerId: Long,
  providerOwnerId: Long,
  extId: String,
  name: String,
  device: Option[String],
  `type`: ProviderType,
  location: Option[String]
)

object ZipProviderDetailed extends JsonConfig {
  implicit val format: Format[ZipProviderDetailed] = Json.format[ZipProviderDetailed]
}
