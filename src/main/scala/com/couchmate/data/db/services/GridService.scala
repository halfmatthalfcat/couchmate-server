package com.couchmate.data.db.services

import java.time.LocalDateTime

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.api.models.grid.Grid
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.GridDAO

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object GridService extends GridDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("grid-service")

  sealed trait Command {
    val senderRef: ActorRef[GridResult]
  }
  sealed trait GridResult
  sealed trait GridResultSuccess[T] extends GridResult {
    val result: T
  }
  sealed trait GridResultFailure extends GridResult {
    val err: Throwable
  }

  final case class GetGrid(
    providerId: Long,
    startTime: LocalDateTime,
    duration: Int,
    senderRef: ActorRef[GridResult]
  ) extends Command
  final case class GetGridSuccess(
    result: Grid
  ) extends GridResultSuccess[Grid]
  final case class GetGridFailure(
    err: Throwable
  ) extends GridResultFailure

  private final case class InternalSuccess[T](
    result: GridResultSuccess[T],
    senderRef: ActorRef[GridResult]
  ) extends Command
  private final case class InternalFailure(
    err: GridResultFailure,
    senderRef: ActorRef[GridResult]
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

      case GetGrid(providerId, startTime, duration, senderRef) =>
        ctx.pipeToSelf(getGrid(
          providerId,
          startTime,
          duration
        )) {
          case Success(value) => InternalSuccess(GetGridSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetGridFailure(exception), senderRef)
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
