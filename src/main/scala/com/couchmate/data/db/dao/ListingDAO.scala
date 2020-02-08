package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.ListingQueries
import com.couchmate.data.db.table.LineupTable
import com.couchmate.data.models.Lineup

import scala.concurrent.{ExecutionContext, Future}

class ListingDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends ListingQueries {

  def getLineup(lineupId: Long): Future[Option[Lineup]] = {
    db.run(super.getLineup(lineupId).result.headOption)
  }

  def lineupsExistForProvider(providerId: Long): Future[Seq[Long]] = {
    db.run(super.lineupsExistForProvider(providerId).result)
  }

  def upsertLineup(lineup: Lineup): Future[Lineup] =
    lineup.lineupId.fold(
      db.run((LineupTable.table returning LineupTable.table) += lineup)
    ) { (lineupId: Long) => db.run(for {
      _ <- LineupTable.table.update(lineup)
      updated <- super.getLineup(lineupId)
    } yield updated.result.head.transactionally)}

}
