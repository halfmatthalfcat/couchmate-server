package com.couchmate.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{ProviderDAO, UserProviderDAO}
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}

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
    val listingCoordinator: ActorRef[ListingCoordinator.Command] =
      SingletonExtension(ctx.system).listingCoordinator

    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(StartUpdate, 1 day)

      // ctx.self ! StartUpdate

      Behaviors.receiveMessage {
        case StartUpdate => ctx.pipeToSelf(getProviders) {
          case Success(value) => ProvidersSuccess(value)
          case Failure(exception) => ProvidersFailure(exception)
        }
          Behaviors.same
        case ProvidersSuccess(providers) =>
          providers.foreach { providerId: Long =>
            listingCoordinator ! ListingCoordinator.RequestListing(
              providerId,
              ctx.system.ignoreRef
            )
          }
          Behaviors.same
      }
    }
  }

  def getProviders()(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[Long]] = for {
    defaults <- getProvidersForType("Default")
    users <- getUniqueProviders()
  } yield (defaults ++ users).map(_.providerId.get)
}
