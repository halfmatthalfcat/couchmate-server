package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.RoomActivityDAO
import com.couchmate.data.models.RoomActivity

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object RoomActivityService extends RoomActivityDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("room-activity-service")

  sealed trait Command {
    val senderRef: ActorRef[RoomActivityResult]
  }
  sealed trait RoomActivityResult
  sealed trait RoomActivityResultSuccess[T] extends RoomActivityResult {
    val result: T
  }
  sealed trait RoomActivityResultFailure extends RoomActivityResult {
    val err: Throwable
  }

  final case class GetRoomCount(
    airingId: UUID,
    senderRef: ActorRef[RoomActivityResult]
  ) extends Command
  final case class GetRoomCountSuccess(
    result: Int
  ) extends RoomActivityResultSuccess[Int]
  final case class GetRoomCountFailure(
    err: Throwable
  ) extends RoomActivityResultFailure

  final case class AddRoomActivity(
    roomActivity: RoomActivity,
    senderRef: ActorRef[RoomActivityResult]
  ) extends Command
  final case class AddRoomActivitySuccess(
    result: RoomActivity
  ) extends RoomActivityResultSuccess[RoomActivity]
  final case class AddRoomActivityFailure(
    err: Throwable
  ) extends RoomActivityResultFailure

  private final case class InternalSuccess[T](
    result: RoomActivityResultSuccess[T],
    senderRef: ActorRef[RoomActivityResult]
  ) extends Command

  private final case class InternalFailure(
    err: RoomActivityResultFailure,
    senderRef: ActorRef[RoomActivityResult]
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

      case GetRoomCount(airingId, senderRef) =>
        ctx.pipeToSelf(getRoomCount(airingId)) {
          case Success(value) => InternalSuccess(GetRoomCountSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetRoomCountFailure(exception), senderRef)
        }
        Behaviors.same
      case AddRoomActivity(roomActivity, senderRef) =>
        ctx.pipeToSelf(addRoomActivity(roomActivity)) {
          case Success(value) => InternalSuccess(AddRoomActivitySuccess(value), senderRef)
          case Failure(exception) => InternalFailure(AddRoomActivityFailure(exception), senderRef)
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