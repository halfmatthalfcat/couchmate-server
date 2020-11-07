package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.coding._
import akka.http.scaladsl.model.Uri.{Query, from}
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import com.couchmate.common.models.api.room.tenor.{TenorGif, TenorSearchItem, TenorSearchResult, TenorTrendingResult}
import com.couchmate.services.user.PersistentUser.{TenorSearched, TenorTrending}
import com.couchmate.util.akka.extensions.UserExtension
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TenorService extends PlayJsonSupport {
  sealed trait Command

  final case class GetTenorTrending(userId: UUID) extends Command
  final case class SearchTenor(userId: UUID, search: String) extends Command

  final case class TenorTrendingResults(userId: UUID, keywords: Seq[String]) extends Command
  final case class TenorTrendingFailure(ex: Throwable) extends Command
  final case class TenorSearchResults(userId: UUID, results: Seq[TenorGif]) extends Command
  final case class TenorSearchFailure(ex: Throwable) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { implicit ctx =>
    implicit val http: HttpExt = Http(ctx.system)
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx.system)
    implicit val userExtension: UserExtension = UserExtension(ctx.system)
    val tenorApiKey: String = ConfigFactory.load().getString("tenor.apiKey")

    Behaviors.receiveMessage {
      case GetTenorTrending(userId) => ctx.pipeToSelf(getTrending(tenorApiKey)) {
          case Success(value) => TenorTrendingResults(userId, value)
          case Failure(exception) => TenorTrendingFailure(exception)
        }
        Behaviors.same
      case TenorTrendingResults(userId, keywords) => userExtension.inClusterMessage(
          userId,
          TenorTrending(keywords)
        )
        Behaviors.same
      case SearchTenor(userId, searchText) => ctx.pipeToSelf(search(tenorApiKey, searchText)) {
          case Success(gifs) => TenorSearchResults(userId, gifs)
          case Failure(exception) => TenorSearchFailure(exception)
        }
        Behaviors.same
      case TenorSearchResults(userId, results) => userExtension.inClusterMessage(
          userId,
          TenorSearched(results)
        )
        Behaviors.same
    }
  }

  def getTrending(apiKey: String)(
    implicit
    ctx: ActorContext[Command],
    http: HttpExt,
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Seq[String]] = for {
    response <- http.singleRequest(HttpRequest(
      uri = Uri.from(
        scheme = "https",
        host = "api.tenor.com",
        path = "/v1/trending_terms"
      ).withQuery(Query(
        "key" -> apiKey
      ))
    ))
    decoded = {
      val decoder = response.encoding match {
        case HttpEncodings.gzip => Gzip
        case HttpEncodings.deflate => Deflate
        case HttpEncodings.identity => NoCoding
        case _ => NoCoding
      }

      decoder.decodeMessage(response)
    }
    data <- Unmarshal(decoded.entity).to[TenorTrendingResult]
  } yield data.results

  def search(
    apiKey: String,
    searchText: String
  )(
    implicit
    ctx: ActorContext[Command],
    http: HttpExt,
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Seq[TenorGif]] = for {
    response <- http.singleRequest(HttpRequest(
      uri = Uri.from(
        scheme = "https",
        host = "api.tenor.com",
        path = "/v1/search"
      ).withQuery(Query(
        "key" -> apiKey,
        "q" -> searchText,
        "media_filter" -> "minimal",
        "limit" -> "50",
      ))
    ))
    decoded = {
      val decoder = response.encoding match {
        case HttpEncodings.gzip => Gzip
        case HttpEncodings.deflate => Deflate
        case HttpEncodings.identity => NoCoding
        case _ => NoCoding
      }

      decoder.decodeMessage(response)
    }
    data <- Unmarshal(decoded.entity).to[TenorSearchResult]
  } yield data
    .results
    .map(result => result.media.headOption.map(media => TenorGif(
      url = media.tinygif.url,
      title = result.title,
      height = media.tinygif.dims.last,
      width = media.tinygif.dims.head
    ))).collect { case Some(gif) => gif }
}
