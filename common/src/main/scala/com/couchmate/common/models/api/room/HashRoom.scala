package com.couchmate.common.models.api.room

import play.api.libs.json.{Format, Json}

case class HashRoom(
  name: String,
  count: Int
)

object HashRoom {
  implicit val format: Format[HashRoom] = Json.format[HashRoom]
}
