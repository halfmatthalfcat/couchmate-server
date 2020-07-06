package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Journal
import com.couchmate.common.util.slick.WithTableQuery

class JournalTable(tag: Tag) extends Table[Journal](tag, "journal") {
  def ordering: Rep[Long] = column[Long]("ordering", O.AutoInc)
  def persistenceId: Rep[String] = column[String]("persistence_id")
  def sequenceNumber: Rep[Long] = column[Long]("sequence_number")
  def deleted: Rep[Boolean] = column[Boolean]("deleted", O.Default(false))
  def tags: Rep[Option[String]] = column[Option[String]]("tags")
  def message: Rep[Array[Byte]] = column[Array[Byte]]("message")

  def * = (
    ordering.?,
    persistenceId,
    sequenceNumber,
    deleted,
    tags,
    message
  ) <> ((Journal.apply _).tupled, Journal.unapply)

  def pk = primaryKey(
    "journal_pk",
    (persistenceId, sequenceNumber)
  )
}

object JournalTable extends WithTableQuery[JournalTable] {
  private[couchmate] val table = TableQuery[JournalTable]
}
