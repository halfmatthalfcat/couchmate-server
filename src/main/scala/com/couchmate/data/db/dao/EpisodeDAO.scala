package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{EpisodeTable, SeriesTable, ShowTable}
import com.couchmate.data.models.{Episode, Series, Show}
import com.couchmate.data.thirdparty.gracenote.GracenoteProgram
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class EpisodeDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getEpisode(episodeId: Long): Future[Option[Episode]] = {
    db.run(EpisodeDAO.getEpisode(episodeId).result.headOption)
  }

  def upsertEpisode(episode: Episode): Future[Episode] = db.run(
    episode.episodeId.fold[DBIO[Episode]](
      (EpisodeTable.table returning EpisodeTable.table) += episode
    ) { (episodeId: Long) => for {
      _ <- EpisodeTable.table.update(episode)
      updated <- EpisodeDAO.getEpisode(episodeId).result.head
    } yield updated}.transactionally
  )
}

object EpisodeDAO {
  private[dao] lazy val getEpisode = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

  private[dao] def getShowFromGracenoteEpisode(program: GracenoteProgram)(
    implicit
    ec: ExecutionContext,
  ): DBIO[Show] =
    for {
      seriesExists <- SeriesDAO.getSeriesByExt(program.seriesId.get).result.headOption
      series <- seriesExists.fold[DBIO[Series]](
        (SeriesTable.table returning SeriesTable.table) += Series(
          seriesId = None,
          seriesName = program.title,
          extId = program.seriesId.get,
          totalEpisodes = None,
          totalSeasons = None,
        )
      )(DBIO.successful)
      episode <- (EpisodeTable.table returning EpisodeTable.table) += Episode(
        episodeId = None,
        seriesId = series.seriesId.get,
        season = program.seasonNumber,
        episode = program.episodeNumber,
      )
      show <- (ShowTable.table returning ShowTable.table) += Show(
        showId = None,
        extId = program.rootId,
        `type` = "episode",
        episodeId = episode.episodeId,
        sportEventId = None,
        title = program.episodeTitle.getOrElse(program.title),
        description = program
          .shortDescription
          .orElse(program.longDescription)
          .getOrElse("N/A"),
        originalAirDate = program.origAirDate,
      )
    } yield show
}
