package com.couchmate.services.thirdparty.gracenote

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl._
import com.couchmate.data.thirdparty.gracenote.GracenoteProvider
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.util.Success

class GracenoteService()(
  implicit
  system: ActorSystem,
  config: Config,
) extends PlayJsonSupport with LazyLogging {
  private[this] lazy val httpPool =
    Http().superPool[String]()

  private[this] val gnKey: String =
    config.getString("gracenote.apiKey")

  private[this] val gnHost: String =
    config.getString("gracenote.host")

  private[this] def getGracenoteRequest(path: Seq[String], qs: Map[String, Option[String]]): HttpRequest = {
    val queryString: String =
      if (qs.isEmpty) {
        ""
      } else {
        qs.collect {
          case (key, Some(value)) => key -> value
        }.foldLeft("?") {
          case (acc, (key, value)) => acc + s"$key=$value&"
        }.dropRight(1)
      }

    HttpRequest(
      method = HttpMethods.GET,
      uri = s"http://$gnHost/${path.mkString("/")}$queryString",
      headers = Seq(
        HttpHeader.parse("Accept-encoding", "gzip"),
      ),
    )
  }

  def getProviders(
    zipCode: String,
    country: Option[String] = None,
  ): Source[Seq[GracenoteProvider], NotUsed] = {
    Source
      .single[(HttpRequest, String)](getGracenoteRequest(
        Seq("listing"),
        Map(
          "zipCode" -> Some(zipCode),
          "country" -> country,
        ),
      ) -> zipCode)
      .via(httpPool)
      .mapAsync(1) {
        case (Success(response: HttpResponse), zipCode) =>
          logger.debug(s"Successfully got Gracenote providers for $zipCode")
          Unmarshal(response.entity).to[Seq[GracenoteProvider]]
      }
  }
}
