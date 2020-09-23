package com.couchmate.util.emoji

import play.api.libs.json.{Format, Json}

case class Emojis(
  emojis: Seq[Emoji]
)

object Emojis {
  implicit val format: Format[Emojis] = Json.format[Emojis]
}
