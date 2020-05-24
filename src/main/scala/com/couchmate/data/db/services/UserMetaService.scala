package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.UserMetaDAO
import com.couchmate.data.models.UserMeta

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserMetaService extends UserMetaDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("user-meta-service")

  sealed trait Command {
    val senderRef: ActorRef[UserMetaResult]
  }
  sealed trait UserMetaResult
  sealed trait UserMetaResultSuccess[T] extends UserMetaResult {
    val result: T
  }
  sealed trait UserMetaResultFailure extends UserMetaResult {
    val err: Throwable
  }

  final case class GetUserMeta(
    userId: UUID,
    senderRef: ActorRef[UserMetaResult]
  ) extends Command
  final case class GetUserMetaSuccess(
    result: Option[UserMeta]
  ) extends UserMetaResultSuccess[Option[UserMeta]]
  final case class GetUserMetaFailure(
    err: Throwable
  ) extends UserMetaResultFailure

  final case class EmailExists(
    email: String,
    senderRef: ActorRef[UserMetaResult]
  ) extends Command
  final case class EmailExistsSuccess(
    result: Boolean
  ) extends UserMetaResultSuccess[Boolean]
  final case class EmailExistsFailure(
    err: Throwable
  ) extends UserMetaResultFailure

  final case class UpsertUserMeta(
    userMeta: UserMeta,
    senderRef: ActorRef[UserMetaResult]
  ) extends Command
  final case class UpsertUserMetaSuccess(
    result: UserMeta
  ) extends UserMetaResultSuccess[UserMeta]
  final case class UpsertUserMetaFailure(
    err: Throwable
  ) extends UserMetaResultFailure

  private final case class InternalSuccess[T](
    result: UserMetaResultSuccess[T],
    senderRef: ActorRef[UserMetaResult]
  ) extends Command

  private final case class InternalFailure(
    err: UserMetaResultFailure,
    senderRef: ActorRef[UserMetaResult]
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

      case GetUserMeta(userId, senderRef) =>
        ctx.pipeToSelf(getUserMeta(userId)) {
          case Success(value) => InternalSuccess(GetUserMetaSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserMetaFailure(exception), senderRef)
        }
        Behaviors.same
      case EmailExists(email, senderRef) =>
        ctx.pipeToSelf(emailExists(email)) {
          case Success(value) => InternalSuccess(EmailExistsSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(EmailExistsFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertUserMeta(userMeta, senderRef) =>
        ctx.pipeToSelf(upsertUserMeta(userMeta)) {
          case Success(value) => InternalSuccess(UpsertUserMetaSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertUserMetaFailure(exception), senderRef)
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