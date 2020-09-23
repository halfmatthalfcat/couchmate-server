package com.couchmate.util.emoji

import play.api.libs.json.{Format, Json}

case class EmojiVariation(
  unified: String,
  non_qualified: Option[String],
  image: String,
  sheet_x: Int,
  sheet_y: Int,
  added_in: String,
  has_img_apple: Boolean,
  has_img_google: Boolean,
  has_img_twitter: Boolean,
  has_img_facebook: Boolean
)

object EmojiVariation {
  implicit val format: Format[EmojiVariation] = Json.format[EmojiVariation]
}
