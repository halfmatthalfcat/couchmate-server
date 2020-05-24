package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.UserExtDAO
import com.couchmate.data.models.{User, UserExt}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserExtService extends UserExtDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("TBD")

  sealed trait Command {
    val senderRef: ActorRef[UserExtResult]
  }
  sealed trait UserExtResult
  sealed trait UserExtResultSuccess[T] extends UserExtResult {
    val result: T
  }
  sealed trait UserExtResultFailure extends UserExtResult {
    val err: Throwable
  }

  final case class GetUserExt(
    userId: UUID,
    senderRef: ActorRef[UserExtResult]
  ) extends Command
  final case class GetUserExtSuccess(
    result: Option[UserExt]
  ) extends UserExtResultSuccess[Option[UserExt]]
  final case class GetUserExtFailure(
    err: Throwable
  ) extends UserExtResultFailure

  final case class UpsertUserExt(
    userExt: UserExt,
    senderRef: ActorRef[UserExtResult]
  ) extends Command
  final case class UpsertUserExtSuccess(
    result: UserExt
  ) extends UserExtResultSuccess[UserExt]
  final case class UpsertUserExtFailure(
    err: Throwable
  ) extends UserExtResultFailure

  private final case class InternalSuccess[T](
    result: UserExtResultSuccess[T],
    senderRef: ActorRef[UserExtResult]
  ) extends Command

  private final case class InternalFailure(
    err: UserExtResultFailure,
    senderRef: ActorRef[UserExtResult]
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

      case GetUserExt(userId, senderRef) =>
        ctx.pipeToSelf(getUserExt(userId)) {
          case Success(value) => InternalSuccess(GetUserExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserExtFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertUserExt(userExt, senderRef) =>
        ctx.pipeToSelf(upsertUserExt(userExt)) {
          case Success(value) => InternalSuccess(UpsertUserExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertUserExtFailure(exception), senderRef)
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