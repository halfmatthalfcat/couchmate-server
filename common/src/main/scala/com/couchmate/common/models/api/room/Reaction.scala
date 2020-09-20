package com.couchmate.common.models.api.room

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class Reaction(
  shortCode: String,
  reactors: Seq[UUID]
)

object Reaction {
  implicit val format: Format[Reaction] = Json.format[Reaction]
}
