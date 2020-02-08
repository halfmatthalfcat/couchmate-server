package com.couchmate.data.models

import java.time.LocalDateTime

import play.api.libs.json.{Json, OFormat}

case class ListingCache(
  listingCacheId: Option[Long],
  providerChannelId: Long,
  startTime: LocalDateTime,
  airings: Seq[Airing],
) extends Product with Serializable

object ListingCache {
  implicit val format: OFormat[ListingCache] = Json.format[ListingCache]
}
