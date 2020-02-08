package com.couchmate.db.dao

import com.couchmate.common.models.Lineup
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.ListingQueries
import com.couchmate.db.table.LineupTable

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
