package com.couchmate.common.models.thirdparty.gracenote

import com.couchmate.common.models.data.ProviderType
import com.neovisionaries.i18n.CountryCode
import play.api.libs.json.{Json, OFormat}

case class GracenoteProvider(
  lineupId: String,
  name: String,
  `type`: ProviderType,
  device: Option[String],
  location: Option[String],
  mso: Option[GracenoteProviderOwner],
) extends Product with Serializable {
  def getOwnerName: String = {
    if (mso.isDefined) {
      cleanName(mso.get.name)
    } else if (`type` == ProviderType.OTA) {
      "OTA"
    } else {
      cleanName(name)
    }
  }

  def getName(countryCode: CountryCode): String = {
    if (
      location.isDefined &&
      !location.contains("None") &&
      countryCode.getAlpha3 != location.getOrElse("")
    ) {
      if (name.toLowerCase.contains("digital")) {
        s"${cleanName(name)} Digital (${location.get})"
      } else {
        s"${cleanName(name)} (${location.get})"
      }
    } else {
      cleanName(name)
    }
  }

  private[this] def cleanName(name: String): String = {
    Seq(
      s"(?i)(${`type`.entryName})".r,
      s"(?i)(${location.getOrElse("")}\\w?(?:.*))".r,
      "(?i)(digital)".r,
      "-".r
    )
      .foldRight(name)(_.replaceAllIn(_, ""))
      .split(" ")
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString(" ")
  }
}

object GracenoteProvider {
  implicit val format: OFormat[GracenoteProvider] = Json.format[GracenoteProvider]
}
