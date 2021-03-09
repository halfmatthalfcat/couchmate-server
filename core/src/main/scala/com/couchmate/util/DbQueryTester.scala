package com.couchmate.util

import com.couchmate.common.dao.UserActivityAnalyticsDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.util.mail.AnalyticsReport
import com.couchmate.util.mail.Fragments.{banner, email, row}
import scalatags.Text.all._

import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object DbQueryTester extends UserActivityAnalyticsDAO {
  def main(args: Array[String]): Unit = {
    implicit val db: Database = Database.forConfig("db")

    val report = Await.result(
      getUserAnalytics,
      Duration.Inf,
    )

    println(email(
      banner(
        "Couchmate Analytics Report",
        Some(report.reportDate.toLocalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
      ) ++ Seq(
        row(h2("User Count")),
        row(AnalyticsReport.userCountTable(report)),
        row(h2("User Session")),
        row(AnalyticsReport.userSessionTable(report))
      ),
    ).toString)
  }
}
