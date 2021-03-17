package com.couchmate.util.zip

import com.neovisionaries.i18n.CountryCode
import play.api.libs.json.Json

import scala.io.Source

object ZipUtils {
  private[this] lazy val zips =
    Json.parse({
      val file = Source.fromResource("zip.json")
      try {
        file.mkString
      } catch {
        case _: Throwable => "[]"
      } finally {
        file.close
      }
    }).as[Seq[ZipCodeValidator]]

  def isValid(zipCode: String, countryCode: CountryCode): Boolean =
    zips.find(_.iso == countryCode)
        .flatMap(_.regex.map(_.matches(zipCode)))
        .getOrElse(false)
}
