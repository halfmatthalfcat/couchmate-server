package com.couchmate.external.gracenote

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import com.couchmate.data.models.SportOrganization
import com.couchmate.external.gracenote.models.{GracenoteChannelAiring, GracenoteProvider, GracenoteSportOrganization, GracenoteSportResponse}
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object GracenoteService extends PlayJsonSupport {
  sealed trait Command {
    val senderRef: ActorRef[GracenoteServiceResult]
  }
  sealed trait GracenoteServiceResult
  sealed trait GracenoteServiceResultSuccess[T] extends GracenoteServiceResult {
    val result: T
  }
  sealed trait GracenoteServiceResultFailure extends GracenoteServiceResult {
    val err: Throwable
  }

  final case class GetProviders(
    zipCode: String,
    country: Option[String],
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command
  final case class GetProvidersSuccess(
    result: Seq[GracenoteProvider]
  ) extends GracenoteServiceResultSuccess[Seq[GracenoteProvider]]
  final case class GetProvidersFailure(
    err: Throwable
  ) extends GracenoteServiceResultFailure

  final case class GetProvider(
    extProviderId: String,
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command
  final case class GetProviderSuccess(
    result: GracenoteProvider
  ) extends GracenoteServiceResultSuccess[GracenoteProvider]
  final case class GetProviderFailure(
    err: Throwable
  ) extends GracenoteServiceResultFailure

  final case class GetListing(
    extListingId: String,
    startDate: LocalDateTime,
    duration: Int,
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command
  final case class GetListingSuccess(
    result: Seq[GracenoteChannelAiring]
  ) extends GracenoteServiceResultSuccess[Seq[GracenoteChannelAiring]]
  final case class GetListingFailure(
    err: Throwable
  ) extends GracenoteServiceResultFailure

  final case class GetSportOrganization(
    sportId: Long,
    orgId: Option[Long],
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command
  final case class GetSportOrganizationSuccess(
    result: SportOrganization
  ) extends GracenoteServiceResultSuccess[SportOrganization]
  final case class GetSportOrganizationFailure(
    err: Throwable
  ) extends GracenoteServiceResultFailure

  private final case class RequestSuccess(
    request: Command,
    entity: ResponseEntity,
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command
  private final case class RequestFailure(
    err: GracenoteServiceResultFailure,
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command

  private final case class DeserializationSuccess[T](
    result: GracenoteServiceResultSuccess[T],
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command
  private final case class DeserializationFailure(
    err: GracenoteServiceResultFailure,
    senderRef: ActorRef[GracenoteServiceResult]
  ) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx)
    val http: HttpExt = Http(ctx.system)
    val config: Config = ConfigFactory.load()
    val gnApiKey: String = config.getString("gracenote.apiKey")
    val gnHost: String = config.getString("gracenote.host")

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case DeserializationSuccess(result, senderRef) =>
        senderRef ! result
        Behaviors.same
      case DeserializationFailure(err, senderRef) =>
        senderRef ! err
        Behaviors.same
      case RequestFailure(err, senderRef) =>
        senderRef ! err
        Behaviors.same

      // -- Execute Requests
      case command @ GetProviders(zipCode, country, senderRef) =>
        ctx.pipeToSelf(http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("lineups"),
          Map(
            "postalCode" -> Some(zipCode),
            "country" -> country,
          )
        ))) {
          case Success(value) => RequestSuccess(
            command,
            value.entity,
            senderRef
          )
          case Failure(exception) => RequestFailure(
            GetProvidersFailure(exception),
            senderRef
          )
        }
        Behaviors.same
      case command @ GetProvider(extProviderId, senderRef) =>
        ctx.pipeToSelf(http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("lineups", extProviderId),
          Map(),
        ))) {
          case Success(value) => RequestSuccess(
            command,
            value.entity,
            senderRef
          )
          case Failure(exception) => RequestFailure(
            GetProviderFailure(exception),
            senderRef
          )
        }
        Behaviors.same
      case command @ GetListing(extListingId, startDate, duration, senderRef) =>
        val offsetDateTime: OffsetDateTime =
          OffsetDateTime.of(startDate, ZoneOffset.UTC)
        ctx.pipeToSelf(http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("lineups", extListingId, "grid"),
          Map(
            "startDateTime" -> Some(
              offsetDateTime.format(DateTimeFormatter.ISO_DATE_TIME)
            ),
            "endDateTime" -> Some(
              offsetDateTime.plusHours(duration).format(DateTimeFormatter.ISO_DATE_TIME)
            )
          )
        ))) {
          case Success(value) => RequestSuccess(
            command,
            value.entity,
            senderRef
          )
          case Failure(exception) => RequestFailure(
            GetListingFailure(exception),
            senderRef
          )
        }
        Behaviors.same
      case command @ GetSportOrganization(sportId, orgId, senderRef) =>
        ctx.pipeToSelf(http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("sports", sportId.toString),
          Map(
            "includeOrg" -> Some("true"),
            "officialOrg" -> Some("true"),
          ),
        ))) {
          case Success(value) => RequestSuccess(
            command,
            value.entity,
            senderRef
          )
          case Failure(exception) => RequestFailure(
            GetListingFailure(exception),
            senderRef
          )
        }
        Behaviors.same

      // -- Decode Requests
      case RequestSuccess(_: GetProviders, entity, senderRef) =>
        ctx.pipeToSelf(Unmarshal(entity).to[Seq[GracenoteProvider]]) {
          case Success(value) => DeserializationSuccess(
            GetProvidersSuccess(value),
            senderRef
          )
          case Failure(exception) => DeserializationFailure(
            GetProvidersFailure(exception),
            senderRef
          )
        }
        Behaviors.same
      case RequestSuccess(_: GetProvider, entity, senderRef) =>
        ctx.pipeToSelf(Unmarshal(entity).to[GracenoteProvider]) {
          case Success(value) => DeserializationSuccess(
            GetProviderSuccess(value),
            senderRef
          )
          case Failure(exception) => DeserializationFailure(
            GetProviderFailure(exception),
            senderRef
          )
        }
        Behaviors.same
      case RequestSuccess(GetListing(_, startDate, _, _), entity, senderRef) =>
        ctx.pipeToSelf(Unmarshal(entity).to[Seq[GracenoteChannelAiring]]) {
          case Success(value) =>
            val payload: Seq[GracenoteChannelAiring] =
              value.map(_.copy(startDate = Some(startDate)))
            DeserializationSuccess(
              GetListingSuccess(payload),
              senderRef
            )
          case Failure(exception) => DeserializationFailure(
            GetListingFailure(exception),
            senderRef
          )
        }
        Behaviors.same
      case RequestSuccess(GetSportOrganization(sportId, orgId, _), entity, senderRef) =>
        ctx.pipeToSelf(Unmarshal(entity).to[Seq[GracenoteSportResponse]]) {
          case Success(value) =>
            val org: Option[GracenoteSportOrganization] =
              orgId.flatMap(orgId => value.head.organizations.find(_.organizationId == orgId))
            DeserializationSuccess(
              GetSportOrganizationSuccess(SportOrganization(
                sportOrganizationId = None,
                extSportId = sportId,
                extOrgId = org.map(_.organizationId),
                sportName = value.head.sportsName,
                orgName = org.map(_.organizationName)
              )),
              senderRef
            )
          case Failure(exception) => DeserializationFailure(
            GetSportOrganizationFailure(exception),
            senderRef
          )
        }
        Behaviors.same
    }

    run()
  }

  private[this] def makeGracenoteRequest(
    host: String,
    key: String,
    path: Seq[String],
    qs: Map[String, Option[String]]
  ): HttpRequest = {
    val defaultQs = Map(
      "api_key" -> Some(key)
    )

    val queryString: String =
      (defaultQs ++ qs).collect {
        case (key, Some(value)) => key -> value
      }.foldLeft("?") {
        case (acc, (key, value)) => acc + s"$key=$value&"
      }.dropRight(1)

    HttpRequest(
      method = HttpMethods.GET,
      uri = s"http://$host/${path.mkString("/")}$queryString",
      headers = Seq(
        RawHeader("Accept-encoding", "gzip"),
      ),
    )
  }
}