package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ProviderChannelDAO
import com.couchmate.data.models.{Channel, ProviderChannel}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProviderChannelService extends ProviderChannelDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("provider-channel-service")

  sealed trait Command {
    val senderRef: ActorRef[ProviderChannelResult]
  }
  sealed trait ProviderChannelResult
  sealed trait ProviderChannelResultSuccess[T] extends ProviderChannelResult {
    val result: T
  }
  sealed trait ProviderChannelResultFailure extends ProviderChannelResult {
    val err: Throwable
  }

  final case class GetProviderChannel(
    providerChannelId: Long,
    senderRef: ActorRef[ProviderChannelResult]
  ) extends Command
  final case class GetProviderChannelSuccess(
    result: Option[ProviderChannel]
  ) extends ProviderChannelResultSuccess[Option[ProviderChannel]]
  final case class GetProviderChannelFailure(
    err: Throwable
  ) extends ProviderChannelResultFailure

  final case class GetProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long,
    senderRef: ActorRef[ProviderChannelResult]
  ) extends Command
  final case class GetProviderChannelForProviderAndChannelSuccess(
    result: Option[ProviderChannel]
  ) extends ProviderChannelResultSuccess[Option[ProviderChannel]]
  final case class GetProviderChannelForProviderAndChannelFailure(
    err: Throwable
  ) extends ProviderChannelResultFailure

  final case class UpsertProviderChannel(
    providerChannel: ProviderChannel,
    senderRef: ActorRef[ProviderChannelResult]
  ) extends Command
  final case class UpsertProviderChannelSuccess(
    result: ProviderChannel
  ) extends ProviderChannelResultSuccess[ProviderChannel]
  final case class UpsertProviderChannelFailure(
    err: Throwable
  ) extends ProviderChannelResultFailure

  private final case class InternalSuccess[T](
    result: ProviderChannelResultSuccess[T],
    senderRef: ActorRef[ProviderChannelResult]
  ) extends Command

  private final case class InternalFailure(
    err: ProviderChannelResultFailure,
    senderRef: ActorRef[ProviderChannelResult]
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

      case GetProviderChannel(providerChannelId, senderRef) =>
        ctx.pipeToSelf(getProviderChannel(providerChannelId)) {
          case Success(value) => InternalSuccess(GetProviderChannelSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderChannelFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderChannelForProviderAndChannel(providerId, channelId, senderRef) =>
        ctx.pipeToSelf(getProviderChannelForProviderAndChannel(providerId, channelId)) {
          case Success(value) => InternalSuccess(GetProviderChannelForProviderAndChannelSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderChannelForProviderAndChannelFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertProviderChannel(providerChannel, senderRef) =>
        ctx.pipeToSelf(upsertProviderChannel(providerChannel)) {
          case Success(value) => InternalSuccess(UpsertProviderChannelSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertProviderChannelFailure(exception), senderRef)
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