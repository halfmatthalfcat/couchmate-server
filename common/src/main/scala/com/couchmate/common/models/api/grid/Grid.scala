package com.couchmate.common.models.api.grid

import java.time.LocalDateTime

import play.api.libs.json.{Json, Format}

case class Grid(
  providerId: Long,
  providerName: String,
  startTime: LocalDateTime,
  pages: Seq[GridPage],
) {
  def getAiringIds: Seq[String] =
    pages
      .flatMap(
        _.channels.flatMap(
          _.airings.map(_.airingId)
        )
      ).distinct
}

object Grid {
  implicit val format: Format[Grid] = Json.format[Grid]
}
