package com.couchmate.data.models

import java.time.LocalDateTime

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class ListingCache(
  listingCacheId: Option[Long],
  providerChannelId: Long,
  startTime: LocalDateTime,
  airings: Seq[Airing],
) extends Product with Serializable

object ListingCache {
  implicit val format: OFormat[ListingCache] = Json.format[ListingCache]
  implicit val getResult: GetResult[ListingCache] = GenGetResult[ListingCache]
}
