package com.couchmate.data.models

import java.time.LocalDateTime

import com.couchmate.external.gracenote.models.GracenoteAiring
import play.api.libs.json.{Json, OFormat}

case class ListingCache(
  listingCacheId: Option[Long],
  providerChannelId: Long,
  startTime: LocalDateTime,
  airings: Seq[GracenoteAiring],
) extends Product with Serializable

object ListingCache {
  implicit val format: OFormat[ListingCache] = Json.format[ListingCache]
}
