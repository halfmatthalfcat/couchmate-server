package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ListingJob
import com.couchmate.common.tables.ListingJobTable

import scala.concurrent.{ExecutionContext, Future}

object ListingJobDAO {
  private[this] lazy val getListingJobQuery = Compiled {
    (listingJobId: Rep[Long]) =>
      ListingJobTable
        .table
        .filter(_.listingJobId === listingJobId)
  }

  def getListingJob(listingJobId: Long)(
    implicit
    db: Database
  ): Future[Option[ListingJob]] =
    db.run(getListingJobQuery(listingJobId).result.headOption)

  private[this] lazy val getListingJobFromProviderQuery = Compiled {
    (providerId: Rep[Long]) =>
      ListingJobTable
        .table
        .filter(_.providerId === providerId)
        .sortBy(_.started)
  }

  def getLastListingJobForProvider(providerId: Long)(
    implicit
    db: Database
  ): Future[Option[ListingJob]] =
    db.run(getListingJobFromProviderQuery(providerId).result.headOption)

  def upsertListingJob(listingJob: ListingJob)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[ListingJob] =
    listingJob.listingJobId.fold[Future[ListingJob]](
      db.run((ListingJobTable.table returning ListingJobTable.table) += listingJob)
    ) { (listingJobId: Long) => for {
      _ <- db.run(ListingJobTable
          .table
          .filter(_.listingJobId === listingJobId)
          .update(listingJob))
      updated <- ListingJobDAO.getListingJob(listingJobId)
    } yield updated.get}
}
