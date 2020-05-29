package com.couchmate.data.db.services

import java.time.LocalDateTime

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ListingCacheDAO
import com.couchmate.data.models.ListingCache

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ListingCacheService extends ListingCacheDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("listing-cache-service")

  sealed trait Command {
    val senderRef: ActorRef[ListingCacheResult]
  }
  sealed trait ListingCacheResult
  sealed trait ListingCacheResultSuccess[T] extends ListingCacheResult {
    val result: T
  }
  sealed trait ListingCacheResultFailure extends ListingCacheResult {
    val err: Throwable
  }

  final case class GetListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
    senderRef: ActorRef[ListingCacheResult]
  ) extends Command
  final case class GetListingCacheSuccess(
    result: Option[ListingCache]
  ) extends ListingCacheResultSuccess[Option[ListingCache]]
  final case class GetListingCacheFailure(
    err: Throwable
  ) extends ListingCacheResultFailure

  final case class UpsertListingCache(
    listingCache: ListingCache,
    senderRef: ActorRef[ListingCacheResult]
  ) extends Command
  final case class UpsertListingCacheSuccess(
    result: ListingCache
  ) extends ListingCacheResultSuccess[ListingCache]
  final case class UpsertListingCacheFailure(
    err: Throwable
  ) extends ListingCacheResultFailure

  private final case class InternalSuccess[T](
    result: ListingCacheResultSuccess[T],
    senderRef: ActorRef[ListingCacheResult]
  ) extends Command

  private final case class InternalFailure(
    err: ListingCacheResultFailure,
    senderRef: ActorRef[ListingCacheResult]
  ) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.system.receptionist ! Receptionist.Register(Group, ctx.self)

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case InternalSuccess(result, senderRef) =>
        senderRef ! result
        Behaviors.same
      case InternalFailure(err, senderRef) =>
        senderRef ! err
        Behaviors.same

      case GetListingCache(providerChannelId, startTime, senderRef) =>
        ctx.pipeToSelf(getListingCache(providerChannelId, startTime)) {
          case Success(value) => InternalSuccess(GetListingCacheSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetListingCacheFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertListingCache(listingCache, senderRef) =>
        ctx.pipeToSelf(upsertListingCache(listingCache)) {
          case Success(value) => InternalSuccess(UpsertListingCacheSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertListingCacheFailure(exception), senderRef)
        }
        Behaviors.same
    }

    run()
  }

  def pool(size: Int): PoolRouter[Command] =
    Routers.pool(size)(
      Behaviors.supervise(apply()).onFailure[Exception](SupervisorStrategy.restart)
    )
}