package com.couchmate.util.emoji

import play.api.libs.json.{Format, Json}

case class Emoji(
  name: Option[String],
  unified: String,
  non_qualified: Option[String],
  docomo: Option[String],
  au: Option[String],
  softbank: Option[String],
  google: Option[String],
  image: String,
  sheet_x: Int,
  sheet_y: Int,
  short_name: String,
  short_names: Seq[String],
  text: Option[String],
  texts: Option[Seq[String]],
  category: String,
  sort_order: Int,
  added_in: String,
  has_img_apple: Boolean,
  has_img_google: Boolean,
  has_img_twitter: Boolean,
  has_img_facebook: Boolean,
  skin_variations: Option[Map[String, EmojiVariation]]
)

object Emoji {
  implicit val format: Format[Emoji] = Json.format[Emoji]
}