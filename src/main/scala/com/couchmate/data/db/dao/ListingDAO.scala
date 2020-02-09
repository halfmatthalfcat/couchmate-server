package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{LineupTable, ProviderChannelTable}
import com.couchmate.data.models.Lineup
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class ListingDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getLineup(lineupId: Long): Future[Option[Lineup]] = {
    db.run(ListingDAO.getLineup(lineupId).result.headOption)
  }

  def lineupsExistForProvider(providerId: Long): Future[Seq[Long]] = {
    db.run(ListingDAO.lineupsExistForProvider(providerId).result)
  }

  def upsertLineup(lineup: Lineup): Future[Lineup] = db.run(
    lineup.lineupId.fold[DBIO[Lineup]](
      (LineupTable.table returning LineupTable.table) += lineup
    ) { (lineupId: Long) => for {
      _ <- LineupTable.table.update(lineup)
      updated <- ListingDAO.getLineup(lineupId).result.head
    } yield updated}.transactionally
  )
}

object ListingDAO {
  private[dao] lazy val getLineup = Compiled { (lineupId: Rep[Long]) =>
    LineupTable.table.filter(_.lineupId === lineupId)
  }

  private[dao] lazy val lineupsExistForProvider = Compiled { (providerId: Rep[Long]) =>
    for {
      l <- LineupTable.table
      pc <- ProviderChannelTable.table if (
        l.providerChannelId === pc.providerChannelId &&
          pc.providerId === providerId
        )
    } yield pc.providerId
  }
}
