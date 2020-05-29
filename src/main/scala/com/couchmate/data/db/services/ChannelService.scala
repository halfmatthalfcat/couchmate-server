package com.couchmate.data.db.services

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.models.Channel
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ChannelDAO

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ChannelService extends ChannelDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("channel-service")

  sealed trait Command {
    val senderRef: ActorRef[ChannelResult]
  }
  sealed trait ChannelResult
  sealed trait ChannelResultSuccess[T] extends ChannelResult {
    val result: T
  }
  sealed trait ChannelResultFailure extends ChannelResult {
    val err: Throwable
  }

  final case class GetChannel(
    channelId: Long,
    senderRef: ActorRef[ChannelResult]
  ) extends Command
  final case class GetChannelSuccess(
    result: Option[Channel]
  ) extends ChannelResultSuccess[Option[Channel]]
  final case class GetChannelFailure(
    err: Throwable
  ) extends ChannelResultFailure

  final case class GetChannelForExt(
    extId: Long,
    senderRef: ActorRef[ChannelResult]
  ) extends Command
  final case class GetChannelForExtSuccess(
    result: Option[Channel]
  ) extends ChannelResultSuccess[Option[Channel]]
  final case class GetChannelForExtFailure(
    err: Throwable
  ) extends ChannelResultFailure

  final case class UpsertChannel(
    channel: Channel,
    senderRef: ActorRef[ChannelResult]
  ) extends Command
  final case class UpsertChannelSuccess(
    result: Channel
  ) extends ChannelResultSuccess[Channel]
  final case class UpsertChannelFailure(
    err: Throwable
  ) extends ChannelResultFailure

  private final case class InternalSuccess[T](
    result: ChannelResultSuccess[T],
    senderRef: ActorRef[ChannelResult]
  ) extends Command
  private final case class InternalFailure(
    err: ChannelResultFailure,
    senderRef: ActorRef[ChannelResult]
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

      case GetChannel(channelId, senderRef) =>
        ctx.pipeToSelf(getChannel(channelId)) {
          case Success(value) => InternalSuccess(GetChannelSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetChannelFailure(exception), senderRef)
        }
        Behaviors.same
      case GetChannelForExt(extId, senderRef) =>
        ctx.pipeToSelf(getChannelForExt(extId)) {
          case Success(value) => InternalSuccess(GetChannelForExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetChannelForExtFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertChannel(channel, senderRef) =>
        ctx.pipeToSelf(upsertChannel(channel)) {
          case Success(value) => InternalSuccess(UpsertChannelSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertChannelFailure(exception), senderRef)
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
