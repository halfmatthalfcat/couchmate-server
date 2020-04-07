package com.couchmate.services.thirdparty.gracenote

import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter

import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.couchmate.data.models.SportOrganization
import com.couchmate.data.thirdparty.gracenote.{GracenoteChannelAiring, GracenoteProvider, GracenoteSportOrganization, GracenoteSportResponse}
import com.couchmate.util.DateUtils
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}

class GracenoteService(
  config: Config,
)(
  implicit
  system: ActorSystem[Nothing],
) extends PlayJsonSupport with LazyLogging {

  private[this] implicit val classicSystem: ClassicActorSystem =
    system.toClassic

  private[this] implicit val materializer: ActorMaterializer =
    ActorMaterializer()(classicSystem)

  private[this] val gnKey: String =
    config.getString("gracenote.apiKey")

  private[this] val gnHost: String =
    config.getString("gracenote.host")

  private[this] def getGracenoteRequest(path: Seq[String], qs: Map[String, Option[String]]): HttpRequest = {
    val defaultQs = Map(
      "api_key" -> Some(gnKey)
    )

    val queryString: String =
      (defaultQs ++ qs).collect {
        case (key, Some(value)) => key -> value
      }.foldLeft("?") {
        case (acc, (key, value)) => acc + s"$key=$value&"
      }.dropRight(1)

    logger.debug(s"http://$gnHost/${path.mkString("/")}$queryString")

    HttpRequest(
      method = HttpMethods.GET,
      uri = s"http://$gnHost/${path.mkString("/")}$queryString",
      headers = Seq(
        RawHeader("Accept-encoding", "gzip"),
      ),
    )
  }

  def getProviders(
    zipCode: String,
    country: Option[String],
  )(
    implicit
    ec: ExecutionContext,
  ): Future[Seq[GracenoteProvider]] = {
    for {
      response <- Http().singleRequest(getGracenoteRequest(
        Seq("lineups"),
        Map(
          "postalCode" -> Some(zipCode),
          "country" -> country,
          ),
        ))
      decodedResponse = Gzip.decodeMessage(response)
      payload <- Unmarshal(decodedResponse.entity).to[Seq[GracenoteProvider]]
    } yield payload
  }

  def getProvider(
    extProviderId: String
  )(
    implicit
    ec: ExecutionContext,
  ): Future[GracenoteProvider] = {
    for {
      response <- Http().singleRequest(getGracenoteRequest(
        Seq("lineups", extProviderId),
        Map(),
      ))
      decodedResponse = Gzip.decodeMessage(response)
      payload <- Unmarshal(decodedResponse.entity).to[GracenoteProvider]
    } yield payload
  }

  def getListing(
    extListingId: String,
    startDate: LocalDateTime = DateUtils.roundNearestHour(LocalDateTime.now(ZoneId.of("UTC"))),
    duration: Int = 1,
  )(
    implicit
    ec: ExecutionContext,
  ): Future[Seq[GracenoteChannelAiring]] = {
    val offsetDateTime: OffsetDateTime =
      OffsetDateTime.of(startDate, ZoneOffset.UTC)
    for {
      response <- Http().singleRequest(getGracenoteRequest(
        Seq("lineups", extListingId, "grid"),
        Map(
          "startDateTime" -> Some(
            offsetDateTime.format(DateTimeFormatter.ISO_DATE_TIME)
          ),
          "endDateTime" -> Some(
            offsetDateTime.plusHours(duration).format(DateTimeFormatter.ISO_DATE_TIME)
          ))
        ))
      decodedResponse = Gzip.decodeMessage(response)
      payload <- Unmarshal(decodedResponse.entity).to[Seq[GracenoteChannelAiring]]
    } yield payload.map(_.copy(startDate = Some(startDate)))
  }

  def getSportOrganization(
    sportId: Long,
    orgId: Option[Long],
  )(
    implicit
    ec: ExecutionContext,
  ): Future[SportOrganization] = {
    for {
      response <- Http().singleRequest(getGracenoteRequest(
        Seq("sports", sportId.toString),
        Map(
          "includeOrg" -> Some("true"),
          "officialOrg" -> Some("true"),
        ),
      ))
      decodedResponse = Gzip.decodeMessage(response)
      payload <- Unmarshal(decodedResponse.entity).to[Seq[GracenoteSportResponse]]
      org = orgId.flatMap(orgId => payload.head.organizations.find(_.organizationId == orgId))
    } yield SportOrganization(
      sportOrganizationId = None,
      extSportId = sportId,
      extOrgId = org.map(_.organizationId),
      sportName = payload.head.sportsName,
      orgName = org.map(_.organizationName)
    )
  }
}

object GracenoteService {
  def apply()(
    implicit
    system: ActorSystem[Nothing],
  ): GracenoteService =
    new GracenoteService(
      ConfigFactory.load()
    )
}