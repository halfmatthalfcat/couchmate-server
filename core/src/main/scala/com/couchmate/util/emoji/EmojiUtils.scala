package com.couchmate.util.emoji

import play.api.libs.json.Json

import scala.io.Source

object EmojiUtils {

  private[this] lazy val emojis: Seq[Emoji] =
    Json.parse(
      Source.fromResource("emoji.json").mkString
    ).as[Seq[Emoji]]

  def getEmoji(unifiedOrShort: String): Option[EmojiContext] =
    emojis.find { emoji =>
      emoji.unified == unifiedOrShort ||
      emoji.short_names.contains(unifiedOrShort) ||
      emoji.skin_variations.fold(false)(
        _.exists(_._2.unified == unifiedOrShort)
      )
    }.map(EmojiContext(unifiedOrShort, _))
}
