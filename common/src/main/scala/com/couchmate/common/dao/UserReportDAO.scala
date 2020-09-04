package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserReport
import com.couchmate.common.tables.UserReportTable

import scala.concurrent.Future

trait UserReportDAO {
  def getUserReportsForReporter(reporterId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserReport]] =
    db.run(UserReportDAO.getUserReportsForReporter(reporterId))

  def getUserReportsForReportee(reporteeId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserReport]] =
    db.run(UserReportDAO.getUserReportsForReportee(reporteeId))

  def addUserReport(userReport: UserReport)(
    implicit
    db: Database
  ): Future[UserReport] =
    db.run(UserReportDAO.addUserReport(userReport))
}

object UserReportDAO {
  private[this] lazy val getUserReportsForReporterQuery = Compiled { (reporterId: Rep[UUID]) =>
    UserReportTable.table.filter(_.reporterId === reporterId)
  }

  private[common] def getUserReportsForReporter(reporterId: UUID): DBIO[Seq[UserReport]] =
    getUserReportsForReporterQuery(reporterId).result

  private[this] lazy val getUserReportsForReporteeQuery = Compiled { (reporteeId: Rep[UUID]) =>
    UserReportTable.table.filter(_.reporteeId === reporteeId)
  }

  private[common] def getUserReportsForReportee(reporteeId: UUID): DBIO[Seq[UserReport]] =
    getUserReportsForReporteeQuery(reporteeId).result

  private[common] def addUserReport(userReport: UserReport): DBIO[UserReport] =
    (UserReportTable.table returning UserReportTable.table) += userReport
}
