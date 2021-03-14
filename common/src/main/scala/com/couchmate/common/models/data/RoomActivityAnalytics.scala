package com.couchmate.common.models.data

import com.couchmate.common.dao.RoomActivityAnalyticsDAO.{RoomActivityAnalyticContent, RoomActivityAnalyticSessions}
import play.api.libs.json.{Format, Json}

case class RoomActivityAnalytics(
  last24: RoomActivityAnalyticContent,
  lastWeek: RoomActivityAnalyticContent,
  lastMonth: RoomActivityAnalyticContent
)

object RoomActivityAnalytics {
  implicit val format: Format[RoomActivityAnalytics] = Json.format[RoomActivityAnalytics]
}
