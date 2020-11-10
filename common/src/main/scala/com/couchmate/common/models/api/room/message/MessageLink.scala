package com.couchmate.common.models.api.room.message

import julienrf.json.derived
import play.api.libs.json.{Format, __}

sealed trait MessageLink {
  val url: String
}

object MessageLink {
  implicit val format: Format[MessageLink] =
    derived.flat.oformat((__ \ "mltype").format[String])
}

case class SiteLink(
  url: String,
  title: String,
  host: String,
  siteName: Option[String],
  description: Option[String],
  imageUrl: Option[String],
  faviconUrl: Option[String]
) extends MessageLink

case class ImageLink(
  url: String
) extends MessageLink

case class VideoLink(
  url: String
) extends MessageLink

case class AudioLink(
  url: String
) extends MessageLink
