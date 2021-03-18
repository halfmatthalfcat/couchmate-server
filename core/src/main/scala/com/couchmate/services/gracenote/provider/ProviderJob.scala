package com.couchmate.services.gracenote.provider

import java.util.UUID
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import akka.util.Timeout
import com.couchmate.common.dao.{ProviderChannelDAO, ProviderDAO, ProviderOwnerDAO, ZipProviderDAO}
import com.couchmate.common.models.api.Provider
import com.couchmate.common.models.data
import com.couchmate.common.models.thirdparty.gracenote.GracenoteProvider
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ProviderOwner, ZipProvider, Provider => InternalProvider}
import com.couchmate.services.GracenoteCoordinator
import com.couchmate.services.gracenote._
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}
import com.neovisionaries.i18n.CountryCode
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.duration._
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
    jobId: UUID,
    zipCode: String,
    countryCode: CountryCode,
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx)
    implicit val db: Database = DatabaseExtension(ctx.system).db
    implicit val timeout: Timeout = 30 seconds

    val gracenoteCoordinator: ActorRef[GracenoteCoordinator.Command] =
      SingletonExtension(ctx.system).gracenoteCoordinator

    ctx.ask(gracenoteCoordinator, GracenoteCoordinator.GetProviders(zipCode, countryCode, _)) {
      case Success(GracenoteCoordinator.GetProvidersSuccess(providers)) => ProvidersSuccess(providers)
      case Success(GracenoteCoordinator.GetProvidersFailed(err)) => ProvidersFailure(err)
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

    def ingestProvider(gProvider: GracenoteProvider): Future[data.Provider] = for {
      owner <- gProvider.mso.fold(addAndGetProviderOwner(
        "unknown",
        "Unknown"
      ))(mso => addAndGetProviderOwner(
        mso.id,
        mso.name
      ))
      provider <- addAndGetProvider(data.Provider(
        providerOwnerId = owner.providerOwnerId.get,
        extId = gProvider.lineupId,
        name = gProvider.getName(countryCode),
        `type` = gProvider.`type`,
        location = gProvider.location,
        device = gProvider.device
      ))
      _ <- getOrAddZipProvider(zipCode, countryCode, provider.providerId.get)
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
