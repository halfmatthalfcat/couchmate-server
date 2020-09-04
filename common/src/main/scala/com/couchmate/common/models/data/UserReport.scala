package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserReport(
  reportId: Option[Long],
  created: Option[LocalDateTime],
  reporterId: UUID,
  reporteeId: UUID,
  reportType: UserReportType,
  message: Option[String]
)

object UserReport {
  implicit val format: Format[UserReport] = Json.format[UserReport]
}
