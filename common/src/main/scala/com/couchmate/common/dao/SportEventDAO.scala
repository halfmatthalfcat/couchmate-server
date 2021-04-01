package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Episode, Series, Show, SportEvent, SportEventTeam, SportOrganization, SportOrganizationTeam, SportTeam}
import com.couchmate.common.tables.SportEventTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object SportEventDAO {
  private[this] lazy val getSportEventQuery = Compiled { (sportEventId: Rep[Long]) =>
    SportEventTable.table.filter(_.sportEventId === sportEventId)
  }

  def getSportEvent(sportEventId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportEvent]] = cache(
    "getSportEvent",
    sportEventId
  )(db.run(getSportEventQuery(sportEventId).result.headOption))(
    bust = bust
  )

  private[this] lazy val getSportEventByNameAndOrgQuery = Compiled {
    (name: Rep[String], orgId: Rep[Long]) =>
      SportEventTable.table.filter { se =>
        se.sportEventTitle === name &&
        se.sportOrganizationId === orgId
      }
  }

  def getSportEventByNameAndOrg(name: String, orgId: Long)(
    bust: Boolean = false
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportEvent]] = cache(
    "getSportEventByNameAndOrg",
    name, orgId
  )(db.run(getSportEventByNameAndOrgQuery(name, orgId).result.headOption))(
    bust = bust
  )

  private[this] def addSportEventForId(se: SportEvent)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addSportEventForId",
    se.sportEventTitle,
    se.sportOrganizationId.getOrElse(0L)
  )(db.run(
    sql"""SELECT insert_or_get_sport_event_id(${se.sportOrganizationId}, ${se.sportEventTitle})"""
      .as[Long].head
  ))()

  private[common] def addOrGetSportEvent(se: SportEvent)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[SportEvent] = cache(
    "addOrGetSportEvent",
    se.sportEventTitle,
    se.sportOrganizationId.getOrElse(0L)
  )(for {
    exists <- getSportEventByNameAndOrg(
      se.sportEventTitle,
      se.sportOrganizationId.getOrElse(0L)
    )()
    se <- exists.fold(for {
      sportEventId <- addSportEventForId(se)
      selected <- getSportEventByNameAndOrg(
        se.sportEventTitle,
        se.sportOrganizationId.getOrElse(0L)
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield se)()

  def getOrAddShow(
    show: Show,
    series: Option[Series],
    episode: Option[Episode],
    sportOrganization: SportOrganization,
    sportEvent: SportEvent,
    teams: Seq[SportTeam],
    homeId: Option[Long]
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Show] = for {
    srs <- series.fold(Future.successful(Option.empty[Series]))(
      series => SeriesDAO.addOrGetSeries(series).map(Option(_))
    )
    e <- episode.fold[Future[Option[Episode]]](Future.successful(Option.empty))(
      ep => srs.fold[Future[Option[Episode]]](Future.successful(Option.empty))(s =>
        EpisodeDAO.addOrGetEpisode(ep.copy(seriesId = s.seriesId)).map(Option(_))
      )
    )
    so <- SportOrganizationDAO.addOrGetSportOrganization(sportOrganization)
    se <- SportEventDAO.addOrGetSportEvent(sportEvent.copy(
      sportOrganizationId = so.sportOrganizationId
    ))
    _ <- Future.sequence(teams.map(team => for {
      st <- SportTeamDAO.addOrGetSportTeam(team)
      sot <- SportOrganizationTeamDAO.addAndGetSportOrganizationTeam(
        st.sportTeamId.get,
        so.sportOrganizationId.get
      )
      set <- SportEventTeamDAO.addOrGetSportEventTeam(SportEventTeam(
        sportEventId = se.sportEventId.get,
        sportOrganizationTeamId = sot.sportOrganizationTeamId.get,
        isHome = homeId.contains(st.extSportTeamId)
      ))
    } yield set))
    s <- ShowDAO.addOrGetShow(show.copy(
      sportEventId = se.sportEventId,
      episodeId = e.flatMap(_.episodeId)
    ))
  } yield s

}
