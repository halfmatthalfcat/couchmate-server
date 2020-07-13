package com.couchmate.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.adapter._
import com.couchmate.common.dao.{ProviderDAO, UserProviderDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.services.gracenote.listing.ListingPullType
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ListingUpdater
  extends ProviderDAO
  with UserProviderDAO {
  sealed trait Command

  private final case class ProvidersSuccess(providers: Seq[Long]) extends Command
  private final case class ProvidersFailure(err: Throwable) extends Command

  private final case object StartUpdate extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db
    val scheduler: QuartzSchedulerExtension = QuartzSchedulerExtension(ctx.system.toClassic)
    val listingCoordinator: ActorRef[ListingCoordinator.Command] =
      SingletonExtension(ctx.system).listingCoordinator

    scheduler.schedule(
      "EveryOtherDay",
      ctx.self.toClassic,
      StartUpdate,
      None
    )

    Behaviors.receiveMessage {
      case StartUpdate => ctx.pipeToSelf(getProviders) {
        case Success(value) => ProvidersSuccess(value)
        case Failure(exception) => ProvidersFailure(exception)
      }
        Behaviors.same
      case ProvidersSuccess(providers) =>
        providers.foreach { providerId: Long =>
          ctx.log.debug(s"Starting listing pull for $providerId")
          listingCoordinator ! ListingCoordinator.RequestListing(
            providerId,
            ListingPullType.Full,
            ctx.system.ignoreRef
          )
        }
        Behaviors.same
    }
  }

  def getProviders()(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[Long]] = for {
    defaults <- getProvidersForType("Default")
    users <- getUniqueProviders()
  } yield (defaults ++ users).map(_.providerId.get).distinct
}
