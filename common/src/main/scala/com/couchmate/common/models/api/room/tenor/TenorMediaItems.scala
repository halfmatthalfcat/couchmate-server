package com.couchmate.common.models.api.room.tenor

import play.api.libs.json.{Format, Json}

case class TenorMediaItems(
  nanogif: TenorMediaItem,
  nanomp4: TenorMediaItem,
  nanowebm: TenorMediaItem,
  tinygif: TenorMediaItem,
  tinymp4: TenorMediaItem,
  tinywebm: TenorMediaItem,
  mediumgif: TenorMediaItem,
  loopedmp4: TenorMediaItem,
  webm: TenorMediaItem,
  gif: TenorMediaItem,
  mp4: TenorMediaItem,
)

object TenorMediaItems {
  implicit val format: Format[TenorMediaItems] = Json.format[TenorMediaItems]
}
