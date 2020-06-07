package com.couchmate.external.gracenote.provider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import com.couchmate.api.models.Provider
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{ProviderChannelDAO, ProviderDAO, ProviderOwnerDAO, ZipProviderDAO}
import com.couchmate.data.models.{ProviderOwner, ZipProvider, Provider => InternalProvider}
import com.couchmate.external.gracenote._
import com.couchmate.external.gracenote.models.{GracenoteProvider, GracenoteProviderOwner}
import com.couchmate.util.akka.extensions.DatabaseExtension
import com.neovisionaries.i18n.CountryCode
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ProviderJob
  extends PlayJsonSupport
  with ProviderDAO
  with ProviderOwnerDAO
  with ProviderChannelDAO
  with ZipProviderDAO {

  sealed trait Command
  case class JobEnded(
    zipCode: String,
    country: CountryCode,
    providers: Seq[Provider]
  ) extends Command
  case class JobFailure(
    zipCode: String,
    country: CountryCode,
    err: Throwable
  ) extends Command
  case class AddListener(actorRef: ActorRef[Command]) extends Command

  private final case class ProvidersSuccess(providers: Seq[GracenoteProvider]) extends Command
  private final case class ProvidersFailure(err: Throwable) extends Command

  def apply(
    zipCode: String,
    countryCode: CountryCode,
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx)
    implicit val db: Database = DatabaseExtension(ctx.system).db
    val http: HttpExt = Http(ctx.system)
    val config: Config = ConfigFactory.load()
    val gnApiKey: String = config.getString("gracenote.apiKey")
    val gnHost: String = config.getString("gracenote.host")

    ctx.pipeToSelf(getProviders(zipCode, countryCode)) {
      case Success(value) => ProvidersSuccess(value)
      case Failure(exception) => ProvidersFailure(exception)
    }

    def run(listeners: Seq[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case AddListener(listener) =>
        run(listeners :+ listener)

      case ProvidersSuccess(providers) =>
        ctx.pipeToSelf(Future.sequence(
          providers.map(ingestProvider)
        )) {
          case Success(providers) => JobEnded(
            zipCode,
            countryCode,
            providers.map(p => Provider(
              providerId = p.providerId.get,
              name = p.name,
              `type` = p.`type`,
              location = p.location
            ))
          )
          case Failure(exception) => JobFailure(zipCode, countryCode, exception)
        }
        Behaviors.same

      case job: JobEnded =>
        listeners.foreach(_ ! job)
        parent ! job
        Behaviors.stopped
      case job: JobFailure =>
        listeners.foreach(_ ! job)
        parent ! job
        Behaviors.stopped
    }

    def getProviders(
      zipCode: String,
      countryCode: CountryCode
    ): Future[Seq[GracenoteProvider]] =
      for {
        response <- http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("lineups"),
          Map(
            "postalCode" -> Some(zipCode),
            "country" -> Some(countryCode.getAlpha3),
          )
        ))
        decoded = Gzip.decodeMessage(response)
        providers <- Unmarshal(decoded.entity).to[Seq[GracenoteProvider]]
      } yield providers

    def ingestProvider(provider: GracenoteProvider): Future[InternalProvider] = for {
      owner <- provider.mso.fold(getOrAddProviderOwner(ProviderOwner(
        providerOwnerId = None,
        extProviderOwnerId = None,
        name = provider.getName(countryCode)
      )))(po => getOrAddProviderOwner(ProviderOwner(
        providerOwnerId = None,
        extProviderOwnerId = Some(po.id),
        name = po.name
      )))
      provider <- getOrAddProvider(provider, owner)
      _ <- getOrAddZipProvider(zipCode, countryCode, provider.providerId.get)
    } yield provider

    def getOrAddProvider(provider: GracenoteProvider, owner: ProviderOwner): Future[InternalProvider] = for {
      exists <- getProviderForExtAndOwner(
        provider.lineupId,
        owner.providerOwnerId
      )
      provider <- exists.fold(
        upsertProvider(InternalProvider(
          providerId = None,
          providerOwnerId = owner.providerOwnerId,
          name = provider.name,
          extId = provider.lineupId,
          `type` = provider.`type`,
          location = provider.location
        ))
      )(Future.successful)
    } yield provider

    def getOrAddZipProvider(
      zipCode: String,
      countryCode: CountryCode,
      providerId: Long,
    ): Future[ZipProvider] = for {
      exists <- getZipProviderForZipAndCode(
        zipCode,
        countryCode,
        providerId
      )
      zipProvider <- exists.fold(
        addZipProvider(ZipProvider(
          zipCode,
          countryCode,
          providerId
        ))
      )(Future.successful)
    } yield zipProvider

    run(Seq(initiate))
  }
}
