package com.couchmate.data.db.services

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.UserProviderDAO
import com.couchmate.data.models.{Provider, UserProvider}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserProviderService extends UserProviderDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("user-provider-service")

  sealed trait Command {
    val senderRef: ActorRef[UserProviderResult]
  }
  sealed trait UserProviderResult
  sealed trait UserProviderResultSuccess[T] extends UserProviderResult {
    val result: T
  }
  sealed trait UserProviderResultFailure extends UserProviderResult {
    val err: Throwable
  }

  final case class GetUserProvider(
    userId: UUID,
    senderRef: ActorRef[UserProviderResult]
  ) extends Command
  final case class GetUserProviderSuccess(
    result: Option[UserProvider]
  ) extends UserProviderResultSuccess[Option[UserProvider]]
  final case class GetUserProviderFailure(
    err: Throwable
  ) extends UserProviderResultFailure

  final case class GetProviders(
    userId: UUID,
    senderRef: ActorRef[UserProviderResult]
  ) extends Command
  final case class GetProvidersSuccess(
    result: Seq[Provider]
  ) extends UserProviderResultSuccess[Seq[Provider]]
  final case class GetProvidersFailure(
    err: Throwable
  ) extends UserProviderResultFailure

  final case class UserProviderExists(
    providerId: Long,
    zipCode: String,
    senderRef: ActorRef[UserProviderResult]
  ) extends Command
  final case class UserProviderExistsSuccess(
    result: Boolean
  ) extends UserProviderResultSuccess[Boolean]
  final case class UserProviderExistsFailure(
    err: Throwable
  ) extends UserProviderResultFailure

  final case class GetUniqueInternalProviders(
    senderRef: ActorRef[UserProviderResult]
  ) extends Command
  final case class GetUniqueInternalProvidersSuccess(
    result: Seq[UserProvider]
  ) extends UserProviderResultSuccess[Seq[UserProvider]]
  final case class GetUniqueInternalProviderFailure(
    err: Throwable
  ) extends UserProviderResultFailure

  final case class GetUniqueProviders(
    senderRef: ActorRef[UserProviderResult]
  ) extends Command
  final case class GetUniqueProvidersSuccess(
    result: Seq[Provider]
  ) extends UserProviderResultSuccess[Seq[Provider]]
  final case class GetUniqueProvidersFailure(
    err: Throwable
  ) extends UserProviderResultFailure

  final case class AddUserProvider(
    userProvider: UserProvider,
    senderRef: ActorRef[UserProviderResult]
  ) extends Command
  final case class AddUserProviderSuccess(
    result: UserProvider
  ) extends UserProviderResultSuccess[UserProvider]
  final case class AddUserProviderFailure(
    err: Throwable
  ) extends UserProviderResultFailure

  private final case class InternalSuccess[T](
    result: UserProviderResultSuccess[T],
    senderRef: ActorRef[UserProviderResult]
  ) extends Command

  private final case class InternalFailure(
    err: UserProviderResultFailure,
    senderRef: ActorRef[UserProviderResult]
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

      case GetUserProvider(userId, senderRef) =>
        ctx.pipeToSelf(getUserProvider(userId)) {
          case Success(value) => InternalSuccess(GetUserProviderSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUserProviderFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviders(userId, senderRef) =>
        ctx.pipeToSelf(getProviders(userId)) {
          case Success(value) => InternalSuccess(GetProvidersSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProvidersFailure(exception), senderRef)
        }
        Behaviors.same
      case UserProviderExists(providerId, zipCode, senderRef) =>
        ctx.pipeToSelf(userProviderExists(providerId, zipCode)) {
          case Success(value) => InternalSuccess(UserProviderExistsSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UserProviderExistsFailure(exception), senderRef)
        }
        Behaviors.same
      case GetUniqueInternalProviders(senderRef) =>
        ctx.pipeToSelf(getUniqueInternalProviders) {
          case Success(value) => InternalSuccess(GetUniqueInternalProvidersSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUniqueInternalProviderFailure(exception), senderRef)
        }
        Behaviors.same
      case GetUniqueProviders(senderRef) =>
        ctx.pipeToSelf(getUniqueProviders) {
          case Success(value) => InternalSuccess(GetUniqueProvidersSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetUniqueProvidersFailure(exception), senderRef)
        }
        Behaviors.same
      case AddUserProvider(userProvider, senderRef) =>
        ctx.pipeToSelf(addUserProvider(userProvider)) {
          case Success(value) => InternalSuccess(AddUserProviderSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(AddUserProviderFailure(exception), senderRef)
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