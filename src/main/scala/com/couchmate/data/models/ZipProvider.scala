package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class ZipProvider(
  zipCode: String,
  providerId: Long,
) extends Product with Serializable

object ZipProvider extends JsonConfig {
  implicit val format: OFormat[ZipProvider] = Json.format[ZipProvider]
  implicit val getResult: GetResult[ZipProvider] = GenGetResult[ZipProvider]
}
