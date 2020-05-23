package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.dao.LineupDAO
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel}
import com.couchmate.external.gracenote.models.GracenoteAiring

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

  final case class GetLineupFromGracenote(
    providerChannel: ProviderChannel,
    airing: Airing,
    senderRef: ActorRef[LineupResult]
  ) extends Command
  final case class GetLineupFromGracenoteSuccess(
    result: Lineup
  ) extends LineupResultSuccess[Lineup]
  final case class GetLineupFromGracenoteFailure(
    err: Throwable
  ) extends LineupResultFailure

  final case class DisableFromGracenote(
    providerChannel: ProviderChannel,
    gnAiring: GracenoteAiring,
    senderRef: ActorRef[LineupResult]
  ) extends Command
  final case class DisableFromGracenoteSuccess(
    result: Unit
  ) extends LineupResultSuccess[Unit]
  final case class DisableFromGracenoteFailure(
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
    implicit lazy val db: Database = Database.forConfig("db")

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
      case GetLineupFromGracenote(providerChannel, airing, senderRef) =>
        ctx.pipeToSelf(getLineupFromGracenote(providerChannel, airing)) {
          case Success(value) => InternalSuccess(GetLineupFromGracenoteSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetLineupFromGracenoteFailure(exception), senderRef)
        }
        Behaviors.same
    }

    run()
  }
}
