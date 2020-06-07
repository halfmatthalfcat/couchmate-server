package com.couchmate.api.ws

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Locale

import com.neovisionaries.i18n.CountryCode

case class GeoContext(
  timezone: String,
  country: Option[CountryCode]
)

object GeoContext {
  def apply(
    locale: String,
    timezone: String
  ): GeoContext = {
    val outFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("z")
    val parsedZone: ZonedDateTime =
      ZonedDateTime.now(ZoneId.of(timezone))

    new GeoContext(
      parsedZone.format(outFormatter),
      Option(CountryCode.getByLocale(Locale.forLanguageTag(locale)))
    )
  }
}
