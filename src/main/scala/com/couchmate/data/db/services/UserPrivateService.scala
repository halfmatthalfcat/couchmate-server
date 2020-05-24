package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.UserPrivateDAO
import com.couchmate.data.models.UserPrivate

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserPrivateService extends UserPrivateDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("user-private-service")

  sealed trait Command {
    val senderRef: ActorRef[UserPrivateResult]
  }
  sealed trait UserPrivateResult
  sealed trait UserPrivateResultSuccess[T] extends UserPrivateResult {
    val result: T
  }
  sealed trait UserPrivateResultFailure extends UserPrivateResult {
    val err: Throwable
  }

  final case class GetUserPrivate(
    userId: UUID,
    senderRef: ActorRef[UserPrivateResult]
  ) extends Command
  final case class GetUserPrivateSuccess(
    result: Option[UserPrivate]
  ) extends UserPrivateResultSuccess[Option[UserPrivate]]
  final case class GetUserPrivateFailure(
    err: Throwable
  ) extends UserPrivateResultFailure

  final case class UpsertUserPrivate(
    userPrivate: UserPrivate,
    senderRef: ActorRef[UserPrivateResult]
  ) extends Command
  final case class UpsertUserPrivateSuccess(
    result: UserPrivate
  ) extends UserPrivateResultSuccess[UserPrivate]
  final case class UpsertUserPrivateFailure(
    err: Throwable
  ) extends UserPrivateResultFailure

  private final case class InternalSuccess[T](
    result: UserPrivateResultSuccess[T],
    senderRef: ActorRef[UserPrivateResult]
  ) extends Command

  private final case class InternalFailure(
    err: UserPrivateResultFailure,
    senderRef: ActorRef[UserPrivateResult]
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

      case GetUserPrivate(userId, senderRef) =>
        ctx.pipeToSelf(getUserPrivate(userId)) {
          case Success(value) => InternalSuccess(GetUserPrivateSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserPrivateFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertUserPrivate(userPrivate, senderRef) =>
        ctx.pipeToSelf(upsertUserPrivate(userPrivate)) {
          case Success(value) => InternalSuccess(UpsertUserPrivateSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertUserPrivateFailure(exception), senderRef)
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