package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.UserDAO
import com.couchmate.data.models.{User, UserExtType}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserService extends UserDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("user-service")

  sealed trait Command {
    val senderRef: ActorRef[UserResult]
  }
  sealed trait UserResult
  sealed trait UserResultSuccess[T] extends UserResult {
    val result: T
  }
  sealed trait UserResultFailure extends UserResult {
    val err: Throwable
  }

  final case class GetUser(
    userId: UUID,
    senderRef: ActorRef[UserResult]
  ) extends Command
  final case class GetUserSuccess(
    result: Option[User]
  ) extends UserResultSuccess[Option[User]]
  final case class GetUserFailure(
    err: Throwable
  ) extends UserResultFailure

  final case class GetUserByEmail(
    email: String,
    senderRef: ActorRef[UserResult]
  ) extends Command
  final case class GetUserByEmailSuccess(
    result: Option[User]
  ) extends UserResultSuccess[Option[User]]
  final case class GetUserByEmailFailure(
    err: Throwable
  ) extends UserResultFailure

  final case class GetUserByExt(
    extType: UserExtType,
    extId: String,
    senderRef: ActorRef[UserResult]
  ) extends Command
  final case class GetUserByExtSuccess(
    result: Option[User]
  ) extends UserResultSuccess[Option[User]]
  final case class GetUserByExtFailure(
    err: Throwable
  ) extends UserResultFailure

  final case class UsernameExists(
    username: String,
    senderRef: ActorRef[UserResult]
  ) extends Command
  final case class UsernameExistsSuccess(
    result: Boolean
  ) extends UserResultSuccess[Boolean]
  final case class UsernameExistsFailure(
    err: Throwable
  ) extends UserResultFailure

  final case class UpsertUser(
    user: User,
    senderRef: ActorRef[UserResult]
  ) extends Command
  final case class UpsertUserSuccess(
    result: User
  ) extends UserResultSuccess[User]
  final case class UpsertUserFailure(
    err: Throwable
  ) extends UserResultFailure

  private final case class InternalSuccess[T](
    result: UserResultSuccess[T],
    senderRef: ActorRef[UserResult]
  ) extends Command

  private final case class InternalFailure(
    err: UserResultFailure,
    senderRef: ActorRef[UserResult]
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

      case GetUser(userId, senderRef) =>
        ctx.pipeToSelf(getUser(userId)) {
          case Success(value) => InternalSuccess(GetUserSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserFailure(exception), senderRef)
        }
        Behaviors.same
      case GetUserByEmail(email, senderRef) =>
        ctx.pipeToSelf(getUserByEmail(email)) {
          case Success(value) => InternalSuccess(GetUserByEmailSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserByEmailFailure(exception), senderRef)
        }
        Behaviors.same
      case GetUserByExt(extType, extId, senderRef) =>
        ctx.pipeToSelf(getUserByExt(extType, extId)) {
          case Success(value) => InternalSuccess(GetUserByExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserByExtFailure(exception), senderRef)
        }
        Behaviors.same
      case UsernameExists(username, senderRef) =>
        ctx.pipeToSelf(usernameExists(username)) {
          case Success(value) => InternalSuccess(UsernameExistsSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UsernameExistsFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertUser(user, senderRef) =>
        ctx.pipeToSelf(upsertUser(user)) {
          case Success(value) => InternalSuccess(UpsertUserSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertUserFailure(exception), senderRef)
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