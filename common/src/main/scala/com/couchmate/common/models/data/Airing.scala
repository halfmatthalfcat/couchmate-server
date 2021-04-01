package com.couchmate.common.models.data

import java.time.LocalDateTime

import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

import scala.util.Random

case class Airing(
  airingId: String,
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  isNew: Boolean
)

object Airing extends JsonConfig {
  implicit val format: Format[Airing] = Json.format[Airing]
  implicit val rowParser: GetResult[Airing] = RowParser[Airing]

  def generateShortcode: String = new Random(
    System.currentTimeMillis + Random.nextLong
  )
    .alphanumeric
    .take(7)
    .mkString("")
}
