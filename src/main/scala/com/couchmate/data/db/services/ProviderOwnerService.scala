package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ProviderOwnerDAO
import com.couchmate.data.models.ProviderOwner
import com.couchmate.external.gracenote.models.GracenoteProvider

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProviderOwnerService extends ProviderOwnerDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("provider-owner-service")

  sealed trait Command {
    val senderRef: ActorRef[ProviderOwnerResult]
  }
  sealed trait ProviderOwnerResult
  sealed trait ProviderOwnerResultSuccess[T] extends ProviderOwnerResult {
    val result: T
  }
  sealed trait ProviderOwnerResultFailure extends ProviderOwnerResult {
    val err: Throwable
  }

  final case class GetProviderOwner(
    providerOwnerId: Long,
    senderRef: ActorRef[ProviderOwnerResult]
  ) extends Command
  final case class GetProviderOwnerSuccess(
    result: Option[ProviderOwner]
  ) extends ProviderOwnerResultSuccess[Option[ProviderOwner]]
  final case class GetProviderOwnerFailure(
    err: Throwable
  ) extends ProviderOwnerResultFailure

  final case class GetProviderOwnerForName(
    name: String,
    senderRef: ActorRef[ProviderOwnerResult]
  ) extends Command
  final case class GetProviderOwnerForNameSuccess(
    result: Option[ProviderOwner]
  ) extends ProviderOwnerResultSuccess[Option[ProviderOwner]]
  final case class GetProviderOwnerForNameFailure(
    err: Throwable
  ) extends ProviderOwnerResultFailure

  final case class GetProviderOwnerForExt(
    extProviderOwnerId: String,
    senderRef: ActorRef[ProviderOwnerResult]
  ) extends Command
  final case class GetProviderOwnerForExtSuccess(
    result: Option[ProviderOwner]
  ) extends ProviderOwnerResultSuccess[Option[ProviderOwner]]
  final case class GetProviderOwnerForExtFailure(
    err: Throwable
  ) extends ProviderOwnerResultFailure

  final case class UpsertProviderOwner(
    providerOwner: ProviderOwner,
    senderRef: ActorRef[ProviderOwnerResult]
  ) extends Command
  final case class UpsertProviderOwnerSuccess(
    result: ProviderOwner
  ) extends ProviderOwnerResultSuccess[ProviderOwner]
  final case class UpsertProviderOwnerFailure(
    err: Throwable
  ) extends ProviderOwnerResultFailure

  final case class GetProviderOwnerFromGracenote(
    gracenoteProvider: GracenoteProvider,
    country: Option[String],
    senderRef: ActorRef[ProviderOwnerResult]
  ) extends Command
  final case class GetProviderOwnerFromGracenoteSuccess(
    result: ProviderOwner
  ) extends ProviderOwnerResultSuccess[ProviderOwner]
  final case class GetProviderOwnerFromGracenoteFailure(
    err: Throwable
  ) extends ProviderOwnerResultFailure

  private final case class InternalSuccess[T](
      result: ProviderOwnerResultSuccess[T],
      senderRef: ActorRef[ProviderOwnerResult]
  ) extends Command

  private final case class InternalFailure(
      err: ProviderOwnerResultFailure,
      senderRef: ActorRef[ProviderOwnerResult]
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

      case GetProviderOwner(providerOwnerId, senderRef) =>
        ctx.pipeToSelf(getProviderOwner(providerOwnerId)) {
          case Success(value) => InternalSuccess(GetProviderOwnerSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderOwnerFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderOwnerForName(name, senderRef) =>
        ctx.pipeToSelf(getProviderOwnerForName(name)) {
          case Success(value) => InternalSuccess(GetProviderOwnerForNameSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderOwnerForNameFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderOwnerForExt(extProviderOwnerId, senderRef) =>
        ctx.pipeToSelf(getProviderOwnerForExt(extProviderOwnerId)) {
          case Success(value) => InternalSuccess(GetProviderOwnerForExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderOwnerForExtFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertProviderOwner(providerOwner, senderRef) =>
        ctx.pipeToSelf(upsertProviderOwner(providerOwner)) {
          case Success(value) => InternalSuccess(UpsertProviderOwnerSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertProviderOwnerFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderOwnerFromGracenote(gracenoteProvider, country, senderRef) =>
        ctx.pipeToSelf(getProviderOwnerFromGracenote(gracenoteProvider, country)) {
          case Success(value) => InternalSuccess(GetProviderOwnerFromGracenoteSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderOwnerFromGracenoteFailure(exception), senderRef)
        }
        Behaviors.same
    }

    run()
  }

  def pool(size: Int): PoolRouter[Command] =
    Routers.pool(size)(
      Behaviors
        .supervise(apply())
        .onFailure[Exception](SupervisorStrategy.restart)
    )
}
