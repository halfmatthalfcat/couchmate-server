package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json.{Json, OFormat}

case class GracenoteProvider(
  lineupId: String,
  name: String,
  `type`: String,
  device: Option[String],
  location: Option[String],
  mso: Option[GracenoteProviderOwner],
) extends Product with Serializable {
  def getOwnerName: String = {
    if (mso.isDefined) {
      cleanName(mso.get.name)
    } else if (`type` == "OTA") {
      "OTA"
    } else {
      cleanName(name)
    }
  }

  def getName(country: Option[String]): String = {
    if (location != "None" && country.getOrElse("") != location.getOrElse("")) {
      if (name.toLowerCase.contains("digital")) {
        s"${cleanName(name)} Digital ($location)"
      } else {
        s"${cleanName(name)} ($location)"
      }
    } else {
      cleanName(name)
    }
  }

  private[this] def cleanName(name: String): String = {
    Seq(
      s"(?i)(${`type`})".r,
      s"(?i)(${location.getOrElse("")})".r,
      "(?i)(digital)".r,
      "-".r
    )
      .foldRight(name)(_.replaceAllIn(_, ""))
      .split(" ")
      .map(_.trim)
      .filter(!_.isEmpty)
      .mkString(" ")
  }
}

object GracenoteProvider {
  implicit val format: OFormat[GracenoteProvider] = Json.format[GracenoteProvider]
}
