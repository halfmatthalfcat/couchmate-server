package com.couchmate.services.thirdparty.gracenote

import java.time.{OffsetDateTime, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl._
import com.couchmate.data.thirdparty.gracenote.{GracenoteChannelAiring, GracenoteProvider}
import com.couchmate.util.DateUtils
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.util.Success

class GracenoteService(
  config: Config,
)(
  implicit
  system: ActorSystem,
) extends PlayJsonSupport with LazyLogging {
  private[this] lazy val httpPool =
    Http().superPool[String]()

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
    country: Option[String] = Some("USA"),
  ): Source[GracenoteProvider, NotUsed] =
    Source.single(
      getGracenoteRequest(
        Seq("lineups"),
        Map(
          "postalCode" -> Some(zipCode),
          "country" -> country,
        ),
      ) -> zipCode
    )
      .via(httpPool)
      .flatMapConcat {
        case (Success(response: HttpResponse), zipCode) =>
          logger.debug(s"Successfully got Gracenote providers for $zipCode")
          val decodedResponse: HttpResponse =
            Gzip.decodeMessage(response)
          Source.future(
            Unmarshal(decodedResponse.entity).to[Seq[GracenoteProvider]]
          ).mapConcat(identity)
      }

  def getListing(
    extListingId: String,
    startDate: OffsetDateTime = DateUtils.roundNearestHour(OffsetDateTime.now(ZoneId.of("UTC"))),
    duration: Int = 6,
  ): Source[GracenoteChannelAiring, NotUsed] =
    Source.single(
      getGracenoteRequest(
        Seq("lineups", extListingId, "grid"),
        Map(
          "startDateTime" -> Some(
            startDate.format(DateTimeFormatter.ISO_DATE_TIME)
          ),
          "endDateTime" -> Some(
            startDate.plusHours(duration).format(DateTimeFormatter.ISO_DATE_TIME)
          )
        )
      ) -> extListingId,
    )
    .via(httpPool)
    .flatMapConcat {
      case (Success(response: HttpResponse), listingId) =>
        logger.debug(s"Successfully got Gracenote listing for $listingId")
        val decodedResponse: HttpResponse =
          Gzip.decodeMessage(response)
        Source.future(
          Unmarshal(decodedResponse.entity).to[Seq[GracenoteChannelAiring]]
        ).mapConcat(identity)
    }
}
