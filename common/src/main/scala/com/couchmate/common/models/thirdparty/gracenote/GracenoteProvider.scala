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
    val cleanedName = cleanName(name)
    val cableMod = device.map {
      case "X" if `type` == ProviderType.Cable => " Digital "
      case "L" if `type` == ProviderType.Cable => " Digital Rebuild "
      case _ if `type` == ProviderType.Cable => " Standard "
      case _ => " "
    }.getOrElse(" ")
    val loc = location.flatMap(l => {
      val subLocRegex = "(.+)\\s(?:\\((.+)\\))".r
      if (subLocRegex.matches(l)) {
        val locParts = subLocRegex.findAllIn(l)
        Some(s"${locParts.group(1)} - ${locParts.group(2)}")
      } else if (
        l != countryCode.getAlpha3 &&
        l != "None"
      ) {
        Some(l)
      } else { Option.empty }
    }).map(l => s"($l)").getOrElse("")
    s"$cleanedName$cableMod$loc"
  }

  private[this] def cleanName(name: String): String = {
    Seq(
      s"(?i)(${`type`.entryName})".r,
      s"(?i)(${location.getOrElse("")})".r,
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
