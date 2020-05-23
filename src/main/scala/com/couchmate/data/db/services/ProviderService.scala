package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ProviderDAO
import com.couchmate.data.models.{Provider, ProviderOwner}
import com.couchmate.external.gracenote.models.GracenoteProvider

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProviderService extends ProviderDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("provider-service")

  sealed trait Command {
    val senderRef: ActorRef[ProviderResult]
  }
  sealed trait ProviderResult
  sealed trait ProviderResultSuccess[T] extends ProviderResult {
    val result: T
  }
  sealed trait ProviderResultFailure extends ProviderResult {
    val err: Throwable
  }

  final case class GetProvider(
    providerId: Long,
    senderRef: ActorRef[ProviderResult]
  ) extends Command
  final case class GetProviderSuccess(
    result: Option[Provider]
  ) extends ProviderResultSuccess[Option[Provider]]
  final case class GetProviderFailure(
    err: Throwable
  ) extends ProviderResultFailure

  final case class GetProviderForExtAndOwner(
    extId: String,
    providerOwnerId: Option[Long],
    senderRef: ActorRef[ProviderResult]
  ) extends Command
  final case class GetProviderForExtAndOwnerSuccess(
    result: Option[Provider]
  ) extends ProviderResultSuccess[Option[Provider]]
  final case class GetProviderForExtAndOwnerFailure(
    err: Throwable
  ) extends ProviderResultFailure

  final case class UpsertProvider(
    provider: Provider,
    senderRef: ActorRef[ProviderResult]
  ) extends Command
  final case class UpsertProviderSuccess(
    result: Provider
  ) extends ProviderResultSuccess[Provider]
  final case class UpsertProviderFailure(
    err: Throwable
  ) extends ProviderResultFailure

  final case class GetProviderFromGracenote(
    provider: GracenoteProvider,
    owner: ProviderOwner,
    country: Option[String],
    senderRef: ActorRef[ProviderResult]
  ) extends Command
  final case class GetProviderFromGracenoteSuccess(
    result: Provider
  ) extends ProviderResultSuccess[Provider]
  final case class GetProviderFromGracenoteFailure(
    err: Throwable
  ) extends ProviderResultFailure

  private final case class InternalSuccess[T](
    result: ProviderResultSuccess[T],
    senderRef: ActorRef[ProviderResult]
  ) extends Command

  private final case class InternalFailure(
    err: ProviderResultFailure,
    senderRef: ActorRef[ProviderResult]
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

      case GetProvider(providerId, senderRef) =>
        ctx.pipeToSelf(getProvider(providerId)) {
          case Success(value) => InternalSuccess(GetProviderSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderForExtAndOwner(extId, providerOwnerId, senderRef) =>
        ctx.pipeToSelf(getProviderForExtAndOwner(extId, providerOwnerId)) {
          case Success(value) => InternalSuccess(GetProviderForExtAndOwnerSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderForExtAndOwnerFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertProvider(provider, senderRef) =>
        ctx.pipeToSelf(upsertProvider(provider)) {
          case Success(value) => InternalSuccess(UpsertProviderSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertProviderFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderFromGracenote(provider, owner, country, senderRef) =>
        ctx.pipeToSelf(getProviderFromGracenote(provider, owner, country)) {
          case Success(value) => InternalSuccess(GetProviderFromGracenoteSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderFromGracenoteFailure(exception), senderRef)
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