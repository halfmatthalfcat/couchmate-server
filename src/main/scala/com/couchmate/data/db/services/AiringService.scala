package com.couchmate.data.db.services

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.typed.scaladsl.PoolRouter
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import com.couchmate.data.models.Airing
import com.couchmate.external.gracenote.models.GracenoteAiring
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.AiringDAO

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object AiringService extends AiringDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("airing-service")

  sealed trait Command {
    val senderRef: ActorRef[AiringResult]
  }
  sealed trait AiringResult
  sealed trait AiringResultSuccess[T] extends AiringResult {
    val result: T
  }
  sealed trait AiringResultFailure extends AiringResult {
    val err: Throwable
  }

  final case class GetAiring(
    airingId: UUID,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringSuccess(
    result: Option[Airing]
  ) extends AiringResultSuccess[Option[Airing]]
  final case class GetAiringError(
    err: Throwable
  ) extends AiringResultFailure

  final case class GetAiringsByStart(
    startTime: LocalDateTime,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringsByStartSuccess(
    result: Seq[Airing]
  ) extends AiringResultSuccess[Seq[Airing]]
  final case class GetAiringsByStartFailure(
    err: Throwable
  ) extends AiringResultFailure

  final case class GetAiringsByEnd(
    endTime: LocalDateTime,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringsByEndSuccess(
    result: Seq[Airing]
  ) extends AiringResultSuccess[Seq[Airing]]
  final case class GetAiringsByEndFailure(
    err: Throwable
  ) extends AiringResultFailure

  final case class GetAiringsByStartAndDuration(
    startTime: LocalDateTime,
    duration: Int,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringsByStartAndDurationSuccess(
    result: Seq[Airing]
  ) extends AiringResultSuccess[Seq[Airing]]
  final case class GetAiringsByStartAndDurationFailure(
    err: Throwable
  ) extends AiringResultFailure

  final case class UpsertAiring(
    airing: Airing,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class UpsertAiringSuccess(
    result: Airing
  ) extends AiringResultSuccess[Airing]
  final case class UpsertAiringFailure(
    err: Throwable
  ) extends AiringResultFailure

  final case class GetAiringFromGracenote(
    showId: Long,
    airing: GracenoteAiring,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringFromGracenoteSuccess(
    result: Airing
  ) extends AiringResultSuccess[Airing]
  final case class GetAiringFromGracenoteFailure(
    err: Throwable
  ) extends AiringResultFailure

  private final case class InternalSuccess[T](
    result: AiringResultSuccess[T],
    senderRef: ActorRef[AiringResult]
  ) extends Command
  private final case class InternalFailure(
    err: AiringResultFailure,
    senderRef: ActorRef[AiringResult]
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

      case GetAiring(airingId, senderRef) =>
        ctx.pipeToSelf(getAiring(airingId)) {
          case Success(value) => InternalSuccess(GetAiringSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetAiringError(exception), senderRef)
        }
        Behaviors.same
      case GetAiringsByStart(startTime, senderRef) =>
        ctx.pipeToSelf(getAiringsByStart(startTime)) {
          case Success(value) => InternalSuccess(GetAiringsByStartSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetAiringsByStartFailure(exception), senderRef)
        }
        Behaviors.same
      case GetAiringsByEnd(endTime, senderRef) =>
        ctx.pipeToSelf(getAiringsByEnd(endTime)) {
          case Success(value) => InternalSuccess(GetAiringsByEndSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetAiringsByEndFailure(exception), senderRef)
        }
        Behaviors.same
      case GetAiringsByStartAndDuration(startTime, duration, senderRef) =>
        ctx.pipeToSelf(getAiringsByStartAndDuration(startTime, duration)) {
          case Success(value) => InternalSuccess(GetAiringsByStartAndDurationSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetAiringsByStartAndDurationFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertAiring(airing, senderRef) =>
        ctx.pipeToSelf(upsertAiring(airing)) {
          case Success(value) => InternalSuccess(UpsertAiringSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertAiringFailure(exception), senderRef)
        }
        Behaviors.same
      case GetAiringFromGracenote(showId, airing, senderRef) =>
        ctx.pipeToSelf(getAiringFromGracenote(showId, airing)) {
          case Success(value) => InternalSuccess(GetAiringFromGracenoteSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetAiringFromGracenoteFailure(exception), senderRef)
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
