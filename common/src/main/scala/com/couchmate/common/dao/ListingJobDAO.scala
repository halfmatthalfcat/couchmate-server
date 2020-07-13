package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ListingJob
import com.couchmate.common.tables.ListingJobTable

import scala.concurrent.{ExecutionContext, Future}

trait ListingJobDAO {

  def getListingJob(listingJobId: Long)(
    implicit
    db: Database
  ): Future[Option[ListingJob]] =
    db.run(ListingJobDAO.getListingJob(listingJobId))

  def getLastListingJobForProvider(providerId: Long)(
    implicit
    db: Database
  ): Future[Option[ListingJob]] =
    db.run(ListingJobDAO.getLastListingJob(providerId))

  def upsertListingJob(listingJob: ListingJob)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[ListingJob] =
    db.run(ListingJobDAO.upsertListingJob(listingJob))

}

object ListingJobDAO {
  private[this] lazy val getListingJobQuery = Compiled {
    (listingJobId: Rep[Long]) =>
      ListingJobTable
        .table
        .filter(_.listingJobId === listingJobId)
  }

  private[common] def getListingJob(listingJobId: Long): DBIO[Option[ListingJob]] =
    getListingJobQuery(listingJobId).result.headOption

  private[this] lazy val getListingJobFromProviderQuery = Compiled {
    (providerId: Rep[Long]) =>
      ListingJobTable
        .table
        .filter(_.providerId === providerId)
        .sortBy(_.started)
  }

  private[common] def getLastListingJob(providerId: Long): DBIO[Option[ListingJob]] =
    getListingJobFromProviderQuery(providerId).result.headOption

  private[common] def upsertListingJob(listingJob: ListingJob)(
    implicit
    ec: ExecutionContext
  ): DBIO[ListingJob] =
    listingJob.listingJobId.fold[DBIO[ListingJob]](
      (ListingJobTable.table returning ListingJobTable.table) += listingJob
    ) { (listingJobId: Long) => for {
      _ <- ListingJobTable
          .table
          .filter(_.listingJobId === listingJobId)
          .update(listingJob)
      updated <- ListingJobDAO.getListingJob(listingJobId)
    } yield updated.get}
}
