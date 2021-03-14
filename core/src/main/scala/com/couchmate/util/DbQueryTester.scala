package com.couchmate.util

import com.couchmate.common.dao.{RoomActivityAnalyticsDAO, UserActivityAnalyticsDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.util.mail.AnalyticsReport
import com.couchmate.util.mail.Fragments.{banner, email, row}
import play.api.libs.json.Json
import scalatags.Text.all._

import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object DbQueryTester extends RoomActivityAnalyticsDAO with UserActivityAnalyticsDAO {
  def main(args: Array[String]): Unit = {
    implicit val db: Database = Database.forConfig("db")

    val userAnalyticsReport = Await.result(
      getUserAnalytics,
      Duration.Inf,
    )

    val roomAnalyticsReport = Await.result(
      getRoomAnalytics,
      Duration.Inf
    )

    println(email(
      banner(
        "Couchmate Analytics Report",
        Some(userAnalyticsReport.reportDate.toLocalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
      ) ++ Seq(
        row(h2("User Count")),
        row(AnalyticsReport.userCountTable(userAnalyticsReport)),
        row(h2("User Session")),
        row(AnalyticsReport.userSessionTable(userAnalyticsReport)),
        row(h2("Room Stats (24h)")),
        row(AnalyticsReport.roomActivityTable(roomAnalyticsReport.last24)),
        row(h2("Room Stats (7d)")),
        row(AnalyticsReport.roomActivityTable(roomAnalyticsReport.lastWeek)),
        row(h2("Room Stats (30d)")),
        row(AnalyticsReport.roomActivityTable(roomAnalyticsReport.lastMonth))
      ),
    ).toString)
  }
}
