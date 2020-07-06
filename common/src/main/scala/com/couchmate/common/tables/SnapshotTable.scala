package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Snapshot
import com.couchmate.common.util.slick.WithTableQuery

class SnapshotTable(tag: Tag) extends Table[Snapshot](tag, "snapshot") {
  def persistenceId: Rep[String] = column[String]("persistence_id")
  def sequenceNumber: Rep[Long] = column[Long]("sequence_number")
  def created: Rep[Long] = column[Long]("created")
  def snapshot: Rep[Array[Byte]] = column[Array[Byte]]("snapshot")

  def * = (
    persistenceId,
    sequenceNumber,
    created,
    snapshot
  ) <> ((Snapshot.apply _).tupled, Snapshot.unapply)

  def pk = primaryKey(
    "snapshot_pk",
    (persistenceId, sequenceNumber)
  )
}

object SnapshotTable extends WithTableQuery[SnapshotTable] {
  private[couchmate] val table = TableQuery[SnapshotTable]
}
