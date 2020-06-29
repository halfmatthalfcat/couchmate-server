package com.couchmate.common.models.data

import java.time.LocalDateTime

import com.couchmate.common.models.thirdparty.gracenote.GracenoteAiring
import play.api.libs.json.{Format, Json}

case class ListingCache(
  listingCacheId: Option[Long],
  providerChannelId: Long,
  startTime: LocalDateTime,
  airings: Seq[GracenoteAiring],
) extends Product with Serializable

object ListingCache {
  implicit val format: Format[ListingCache] = Json.format[ListingCache]
}
