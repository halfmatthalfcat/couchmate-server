package com.couchmate.services

import java.time.LocalDateTime

import gracenote._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteChannelAiring, GracenoteProvider, GracenoteSport}
import com.neovisionaries.i18n.CountryCode
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GracenoteCoordinator extends PlayJsonSupport {
  sealed trait Command

  sealed trait Request extends Command {
    val senderRef: ActorRef[Command]
  }

  final case class GetProviders(
    zipCode: String,
    countryCode: CountryCode,
    senderRef: ActorRef[Command]
  ) extends Request
  final case class GetProvidersSuccess(providers: Seq[GracenoteProvider]) extends Command
  final case class GetProvidersFailed(err: Throwable) extends Command

  private final case class GetProvidersResponse(
    providers: Seq[GracenoteProvider],
    senderRef: ActorRef[Command]
  )
  private final case class GetProvidersQueued(
    zipCode: String,
    countryCode: CountryCode
  ) extends Command
  private final case class GetProviderQueueFailure(
    err: Throwable,
    senderRef: ActorRef[Command]
  ) extends Command

  final case class GetListings(
    extId: String,
    slot: LocalDateTime,
    senderRef: ActorRef[Command]
  ) extends Request
  final case class GetListingsSuccess(slot: LocalDateTime, listings: Seq[GracenoteChannelAiring]) extends Command
  final case class GetListingsFailure(err: Throwable) extends Command

  private final case class GetListingsResponse(
    slot: LocalDateTime,
    listings: Seq[GracenoteChannelAiring],
    senderRef: ActorRef[Command]
  )
  private final case class GetListingsQueued(
    extId: String,
    slot: LocalDateTime
  ) extends Command
  private final case class GetListingsQueueFailure(
    err: Throwable,
    senderRef: ActorRef[Command]
  ) extends Command

  final case class GetSports(senderRef: ActorRef[Command]) extends Request
  final case class GetSportsSuccess(sports: Seq[GracenoteSport]) extends Command
  final case class GetSportsFailure(err: Throwable) extends Command

  private final case class GetSportsResponse(
    sports: Seq[GracenoteSport],
    senderRef: ActorRef[Command]
  )
  private final case object GetSportsQueued extends Command
  private final case class GetSportsQueueFailure(
    err: Throwable,
    senderRef: ActorRef[Command]
  ) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx)
    implicit val http: HttpExt = Http(ctx.system)
    implicit val config: Config = ConfigFactory.load()

    val gnHost: String =
      config.getString("gracenote.host")
    val gnApiKey: String =
      config.getString("gracenote.apiKey")

    val queue: SourceQueueWithComplete[Command] = Source.queue[Command](
      50,
      OverflowStrategy.dropHead
    )
    .throttle(5, 1 second)
    .collect {
      case GetProviders(zipCode, countryCode, senderRef) => http.singleRequest(makeGracenoteRequest(
        gnHost,
        gnApiKey,
        Seq("lineups"),
        Map(
          "postalCode" -> Some(zipCode),
          "country" -> Some(countryCode.getAlpha3),
        )
      )).map(Gzip.decodeMessage(_))
        .flatMap(Unmarshal(_).to[Seq[GracenoteProvider]])
        .map(GetProvidersResponse(_, senderRef))
      case GetSports(senderRef) => http.singleRequest(makeGracenoteRequest(
        config.getString("gracenote.host"),
        config.getString("gracenote.apiKey"),
        Seq("sports", "all"),
        Map(
          "includeOrg" -> Some("true"),
          "officialOrg" -> Some("true")
        )
      )).map(Gzip.decodeMessage(_))
       .flatMap(Unmarshal(_).to[Seq[GracenoteSport]])
       .map(GetSportsResponse(_, senderRef))
      case GetListings(extId, slot, senderRef) => http.singleRequest(makeGracenoteRequest(
        config.getString("gracenote.host"),
        config.getString("gracenote.apiKey"),
        Seq("lineups", extId, "grid"),
        Map(
          "startDateTime" -> Some(slot.toString),
          "endDateTime" -> Some(
            slot.plusHours(1).toString
          )
        )
      )).map(Gzip.decodeMessage(_))
        .flatMap(Unmarshal(_).to[Seq[GracenoteChannelAiring]])
        .map(GetListingsResponse(slot, _, senderRef))
    }
    .mapAsync(1)(identity)
    .to(Sink.foreach {
      case GetProvidersResponse(providers, senderRef) =>
        senderRef ! GetProvidersSuccess(providers)
      case GetListingsResponse(slot, listings, senderRef) =>
        senderRef ! GetListingsSuccess(slot, listings)
      case GetSportsResponse(sports, senderRef) =>
        senderRef ! GetSportsSuccess(sports)
    })
    .run()

    Behaviors.receiveMessage {
      case getProviders @ GetProviders(zipCode, countryCode, senderRef) =>
        ctx.pipeToSelf(queue.offer(getProviders)) {
          case Success(QueueOfferResult.Enqueued) =>
            GetProvidersQueued(zipCode, countryCode)
          case Success(QueueOfferResult.Dropped) =>
            GetProviderQueueFailure(
              new RuntimeException("Command dropped, try again"),
              senderRef
            )
          case Success(QueueOfferResult.QueueClosed) =>
            GetProviderQueueFailure(
              new RuntimeException("Queue closed, try again"),
              senderRef
            )
          case Success(QueueOfferResult.Failure(ex)) =>
            GetProviderQueueFailure(
              ex,
              senderRef
            )
          case Failure(exception) => GetProviderQueueFailure(
            exception,
            senderRef
          )
        }
        Behaviors.same
      case GetProvidersQueued(zipCode, countryCode) =>
        ctx.log.debug(s"Queued request for Gracenote Providers: $zipCode/${countryCode.getAlpha3}")
        Behaviors.same
      case GetProviderQueueFailure(err, senderRef) =>
        senderRef ! GetProvidersFailed(err)
        Behaviors.same
      case getListings @ GetListings(extId, slot, senderRef) =>
        ctx.pipeToSelf(queue.offer(getListings)) {
          case Success(QueueOfferResult.Enqueued) =>
            GetListingsQueued(extId, slot)
          case Success(QueueOfferResult.Dropped) =>
            GetListingsQueueFailure(
              new RuntimeException("Command dropped, try again"),
              senderRef
            )
          case Success(QueueOfferResult.QueueClosed) =>
            GetListingsQueueFailure(
              new RuntimeException("Queue closed, try again"),
              senderRef
            )
          case Success(QueueOfferResult.Failure(ex)) =>
            GetListingsQueueFailure(
              ex,
              senderRef
            )
          case Failure(exception) => GetListingsQueueFailure(
            exception,
            senderRef
          )
        }
        Behaviors.same
      case GetListingsQueued(extId, slot) =>
        ctx.log.debug(s"Queued request for Gracenote Listings: ${extId}/${slot}")
        Behaviors.same
      case GetListingsQueueFailure(err, senderRef) =>
        senderRef ! GetListingsFailure(err)
        Behaviors.same
      case getSports @ GetSports(senderRef) =>
        ctx.pipeToSelf(queue.offer(getSports)) {
          case Success(QueueOfferResult.Enqueued) =>
            GetSportsQueued
          case Success(QueueOfferResult.Dropped) =>
            GetSportsQueueFailure(
              new RuntimeException("Command dropped, try again"),
              senderRef
            )
          case Success(QueueOfferResult.QueueClosed) =>
            GetSportsQueueFailure(
              new RuntimeException("Queue closed, try again"),
              senderRef
            )
          case Success(QueueOfferResult.Failure(ex)) =>
            GetSportsQueueFailure(
              ex,
              senderRef
            )
          case Failure(exception) => GetSportsQueueFailure(
            exception,
            senderRef
          )
        }
        Behaviors.same
      case GetSportsQueued =>
        ctx.log.debug(s"Queued request for Gracenote Sports")
        Behaviors.same
      case GetSportsQueueFailure(err, senderRef) =>
        senderRef ! GetSportsFailure(err)
        Behaviors.same
    }
  }
}
