package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.dao.LineupDAO
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object LineupService extends LineupDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("lineup-service")

  sealed trait Command {
    val senderRef: ActorRef[LineupResult]
  }
  sealed trait LineupResult
  sealed trait LineupResultSuccess[T] extends LineupResult {
    val result: T
  }
  sealed trait LineupResultFailure extends LineupResult {
    val err: Throwable
  }

  final case class GetLineup(
    lineupId: Long,
    senderRef: ActorRef[LineupResult]
  ) extends Command
  final case class GetLineupSuccess(
    result: Option[Lineup]
  ) extends LineupResultSuccess[Option[Lineup]]
  final case class GetLineupFailure(
    err: Throwable
  ) extends LineupResultFailure

  final case class LineupsExistForProvider(
    providerId: Long,
    senderRef: ActorRef[LineupResult]
  ) extends Command
  final case class LineupsExistForProviderSuccess(
    result: Boolean
  ) extends LineupResultSuccess[Boolean]
  final case class LineupsExistForProviderFailure(
    err: Throwable
  ) extends LineupResultFailure

  final case class UpsertLineup(
    lineup: Lineup,
    senderRef: ActorRef[LineupResult]
  ) extends Command
  final case class UpsertLineupSuccess(
    result: Lineup
  ) extends LineupResultSuccess[Lineup]
  final case class UpsertLineupFailure(
    err: Throwable
  ) extends LineupResultFailure

  final case class GetLineupForProviderChannelAndAiring(
    providerChannelId: Long,
    airingId: UUID,
    senderRef: ActorRef[LineupResult]
  ) extends Command
  final case class GetLineupForProviderChannelAndAiringSuccess(
    result: Option[Lineup]
  ) extends LineupResultSuccess[Option[Lineup]]
  final case class GetLineupForProviderChannelAndAiringFailure(
    err: Throwable
  ) extends LineupResultFailure

  private final case class InternalSuccess[T](
    result: LineupResultSuccess[T],
    senderRef: ActorRef[LineupResult]
  ) extends Command
  private final case class InternalFailure(
    err: LineupResultFailure,
    senderRef: ActorRef[LineupResult]
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

      case GetLineup(lineupId, senderRef) =>
        ctx.pipeToSelf(getLineup(lineupId)) {
          case Success(value) => InternalSuccess(GetLineupSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetLineupFailure(exception), senderRef)
        }
        Behaviors.same
      case LineupsExistForProvider(providerId, senderRef) =>
        ctx.pipeToSelf(lineupsExistForProvider(providerId)) {
          case Success(value) => InternalSuccess(LineupsExistForProviderSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(LineupsExistForProviderFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertLineup(lineup, senderRef) =>
        ctx.pipeToSelf(upsertLineup(lineup)) {
          case Success(value) => InternalSuccess(UpsertLineupSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertLineupFailure(exception), senderRef)
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
