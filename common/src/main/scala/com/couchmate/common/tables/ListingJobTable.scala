package com.couchmate.common.tables

import java.time.LocalDateTime

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ListingJob, ListingJobStatus}
import com.couchmate.common.util.slick.WithTableQuery

class ListingJobTable(tag: Tag) extends Table[ListingJob](tag, "listing_job") {
  def listingJobId: Rep[Long] = column[Long]("listing_job_id", O.PrimaryKey, O.AutoInc)
  def providerId: Rep[Long] = column[Long]("provider_id")
  def pullAmount: Rep[Int] = column[Int]("pull_amount")
  def started: Rep[LocalDateTime] = column[LocalDateTime]("started")
  def completed: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("completed")
  def baseSlot: Rep[LocalDateTime] = column[LocalDateTime]("base_slot")
  def lastSlot: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("last_slot")
  def status: Rep[ListingJobStatus] = column[ListingJobStatus]("status")

  def * = (
    listingJobId.?,
    providerId,
    pullAmount,
    started,
    completed,
    baseSlot,
    lastSlot,
    status,
  ) <> ((ListingJob.apply _).tupled, ListingJob.unapply)

  def providerFk = foreignKey(
    "listing_job_provider_fk",
    providerId,
    ProviderTable.table
  )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object ListingJobTable extends WithTableQuery[ListingJobTable] {
  private[couchmate] val table: TableQuery[ListingJobTable] = TableQuery[ListingJobTable]
}