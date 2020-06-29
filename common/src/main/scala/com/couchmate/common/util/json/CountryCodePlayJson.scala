package com.couchmate.common.util.json

import com.neovisionaries.i18n.CountryCode
import play.api.libs.json.{JsSuccess, JsValue, Json, Reads, Writes}

trait CountryCodePlayJson {
  implicit val countyCodeReads: Reads[CountryCode] = (json: JsValue) =>
    JsSuccess(CountryCode.getByAlpha3Code(json.as[String]))
  implicit val countryCodeWrites: Writes[CountryCode] = (countryCode: CountryCode) =>
    Json.toJson(countryCode.getAlpha3)
}
