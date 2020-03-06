package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{LineupTable, ProviderChannelTable}
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel}
import com.couchmate.data.thirdparty.gracenote.GracenoteAiring
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class LineupDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getLineup(lineupId: Long): Future[Option[Lineup]] = {
    db.run(LineupDAO.getLineup(lineupId).result.headOption)
  }

  def lineupsExistForProvider(providerId: Long): Future[Seq[Long]] = {
    db.run(LineupDAO.lineupsExistForProvider(providerId).result)
  }

  def upsertLineup(lineup: Lineup): Future[Lineup] = db.run(
    lineup.lineupId.fold[DBIO[Lineup]](
      (LineupTable.table returning LineupTable.table) += lineup
    ) { (lineupId: Long) => for {
      _ <- LineupTable.table.update(lineup)
      updated <- LineupDAO.getLineup(lineupId).result.head
    } yield updated}.transactionally
  )

  def getLineupFromGracenote(
    providerChannel: ProviderChannel,
    airing: Airing,
  ): Future[Lineup] = db.run((for {
    exists <- LineupDAO.getLineupForProviderChannelAndAiring(
      providerChannel.providerChannelId.get,
      airing.airingId.get
    ).result.headOption
    lineup <- exists.fold[DBIO[Lineup]](
      (LineupTable.table returning LineupTable.table) += Lineup(
        lineupId = None,
        providerChannelId = providerChannel.providerChannelId.get,
        airingId = airing.airingId.get,
        active = true,
      )
    )(DBIO.successful)
  } yield lineup).transactionally)

  def disableFromGracenote(
    providerChannel: ProviderChannel,
    gnAiring: GracenoteAiring,
  ): Future[Unit] = db.run((for {
    show <- ShowDAO.getShowByExt(
      gnAiring.program.rootId,
    ).result.headOption
    airing <- show.fold[DBIO[Option[Airing]]](DBIO.successful(None)) { s =>
      AiringDAO.getAiringByShowStartAndEnd(
        s.extId,
        gnAiring.startTime,
        gnAiring.endTime,
      ).result.headOption
    }
    lineup <- airing.fold[DBIO[Option[Lineup]]](DBIO.successful(None)) { a =>
      LineupDAO.getLineupForProviderChannelAndAiring(
        providerChannel.providerChannelId.get,
        a.airingId.get,
      ).result.headOption
    }
    _ <- lineup.fold[DBIO[Unit]](DBIO.successful()) { l =>
      LineupTable.table.update(l.copy(active = false))
      DBIO.successful()
    }
  } yield ()).transactionally)
}

object LineupDAO {
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

  private[dao] lazy val getLineupForProviderChannelAndAiring = Compiled {
    (providerChannelId: Rep[Long], airingId: Rep[UUID]) =>
      LineupTable.table.filter { l =>
        l.providerChannelId === providerChannelId &&
        l.airingId === airingId
      }
  }
}