package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{UserReport, UserReportType}
import com.couchmate.common.util.slick.WithTableQuery

class UserReportTable(tag: Tag) extends Table[UserReport](tag, "user_report") {
  def userReportId: Rep[Long] = column[Long]("user_report_id", O.PrimaryKey, O.AutoInc)
  def created: Rep[LocalDateTime] = column[LocalDateTime]("created")
  def reporterId: Rep[UUID] = column[UUID]("reporter_id")
  def reporteeId: Rep[UUID] = column[UUID]("reportee_id")
  def reportType: Rep[UserReportType] = column[UserReportType]("user_report_type")
  def message: Rep[Option[String]] = column[Option[String]]("message")

  def * = (
    userReportId.?,
    created.?,
    reporterId,
    reporteeId,
    reportType,
    message,
  ) <> ((UserReport.apply _).tupled, UserReport.unapply)

  def reporterFK = foreignKey(
    "reporter_user_fk",
    reporterId,
    UserTable.table,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def reporteeFK = foreignKey(
    "reportee_user_fk",
    reporteeId,
    UserTable.table,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserReportTable extends WithTableQuery[UserReportTable] {
  private[couchmate] val table = TableQuery[UserReportTable]
}