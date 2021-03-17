package com.couchmate.util.zip

import com.neovisionaries.i18n.CountryCode
import play.api.libs.json.{Format, JsSuccess, JsValue, Json, Reads, Writes}

import scala.util.matching.Regex

case class ZipCodeValidator(
  iso: CountryCode,
  name: String,
  regex: Option[Regex],
  range: Option[Seq[String]]
)

object ZipCodeValidator {
  implicit val countyCodeReads: Reads[CountryCode] = (json: JsValue) =>
    JsSuccess(CountryCode.getByAlpha2Code(json.as[String]))
  implicit val countryCodeWrites: Writes[CountryCode] = (countryCode: CountryCode) =>
    Json.toJson(countryCode.getAlpha3)

  implicit val regexReads: Reads[Regex] = (json: JsValue) =>
    JsSuccess(json.as[String].r)
  implicit val regexWrites: Writes[Regex] = (regex: Regex) =>
    Json.toJson(regex.pattern.toString)

  implicit val format: Format[ZipCodeValidator] = Json.format[ZipCodeValidator]
}
