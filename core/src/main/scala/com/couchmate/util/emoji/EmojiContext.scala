package com.couchmate.util.emoji

case class EmojiContext(
  unifiedOrShort: String,
  emoji: Emoji
) {
  def getVariation: Option[EmojiVariation] = emoji
    .skin_variations
    .fold(Option.empty[EmojiVariation])(_.find(_._2.unified == unifiedOrShort).map(_._2))

  def isVariation: Boolean = getVariation.nonEmpty

  def getUnified: String = getVariation
    .map(_.unified)
    .getOrElse(emoji.unified)
}
