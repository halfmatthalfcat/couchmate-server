package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ShowDAO
import com.couchmate.data.models.Show

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ShowService extends ShowDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("show-service")

  sealed trait Command {
    val senderRef: ActorRef[ShowResult]
  }
  sealed trait ShowResult
  sealed trait ShowResultSuccess[T] extends ShowResult {
    val result: T
  }
  sealed trait ShowResultFailure extends ShowResult {
    val err: Throwable
  }

  final case class GetShow(
    showId: Long,
    senderRef: ActorRef[ShowResult]
  ) extends Command
  final case class GetShowSuccess(
    result: Option[Show]
  ) extends ShowResultSuccess[Option[Show]]
  final case class GetShowFailure(
    err: Throwable
  ) extends ShowResultFailure

  final case class GetShowByExt(
    extId: Long,
    senderRef: ActorRef[ShowResult]
  ) extends Command
  final case class GetShowByExtSuccess(
    result: Option[Show]
  ) extends ShowResultSuccess[Option[Show]]
  final case class GetShowByExtFailure(
    err: Throwable
  ) extends ShowResultFailure

  final case class UpsertShow(
    show: Show,
    senderRef: ActorRef[ShowResult]
  ) extends Command
  final case class UpsertShowSuccess(
    result: Show
  ) extends ShowResultSuccess[Show]
  final case class UpsertShowFailure(
    err: Throwable
  ) extends ShowResultFailure

  private final case class InternalSuccess[T](
    result: ShowResultSuccess[T],
    senderRef: ActorRef[ShowResult]
  ) extends Command

  private final case class InternalFailure(
    err: ShowResultFailure,
    senderRef: ActorRef[ShowResult]
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

      case GetShow(showId, senderRef) =>
        ctx.pipeToSelf(getShow(showId)) {
          case Success(value) => InternalSuccess(GetShowSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetShowFailure(exception), senderRef)
        }
        Behaviors.same
      case GetShowByExt(extId, senderRef) =>
        ctx.pipeToSelf(getShowByExt(extId)) {
          case Success(value) => InternalSuccess(GetShowByExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetShowByExtFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertShow(show, senderRef) =>
        ctx.pipeToSelf(upsertShow(show)) {
          case Success(value) => InternalSuccess(UpsertShowSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertShowFailure(exception), senderRef)
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