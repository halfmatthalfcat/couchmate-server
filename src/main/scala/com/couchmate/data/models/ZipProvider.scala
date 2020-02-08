package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class ZipProvider(
  zipCode: String,
  providerId: Long,
) extends Product with Serializable

object ZipProvider extends JsonConfig {
  implicit val format: OFormat[ZipProvider] = Json.format[ZipProvider]
}
