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

  sealed trait Command
  final case class GetAiring(
    airingId: UUID,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringsByStart(
    startTime: LocalDateTime,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringsByEnd(
    endTime: LocalDateTime,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringsByStartAndDuration(
    startTime: LocalDateTime,
    duration: Int,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class UpsertAiring(
    airing: Airing,
    senderRef: ActorRef[AiringResult]
  ) extends Command
  final case class GetAiringFromGracenote(
    showId: Long,
    airing: GracenoteAiring,
    senderRef: ActorRef[AiringResult]
  ) extends Command

  private case class InternalResult[T](
    result: T,
    actorRef: ActorRef[AiringResult]
  ) extends Command
  private case class InternalError(
    err: Throwable,
    actorRef: ActorRef[AiringResult]
  ) extends Command

  sealed trait AiringResult
  final case class Result[T](result: T) extends AiringResult
  final case class Error(err: Throwable) extends AiringResult

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit lazy val db: Database = Database.forConfig("db")

    ctx.system.receptionist ! Receptionist.Register(Group, ctx.self)

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case InternalResult(result, senderRef) =>
        senderRef ! Result(result)
        Behaviors.same
      case InternalError(err, senderRef) =>
        senderRef ! Error(err)
        Behaviors.same

      case GetAiring(airingId, senderRef) =>
        ctx.pipeToSelf(getAiring(airingId)) {
          case Success(value) => InternalResult(value, senderRef)
          case Failure(exception) => InternalError(exception, senderRef)
        }
        Behaviors.same
      case GetAiringsByStart(startTime, senderRef) =>
        ctx.pipeToSelf(getAiringsByStart(startTime)) {
          case Success(value) => InternalResult(value, senderRef)
          case Failure(exception) => InternalError(exception, senderRef)
        }
        Behaviors.same
      case GetAiringsByEnd(endTime, senderRef) =>
        ctx.pipeToSelf(getAiringsByEnd(endTime)) {
          case Success(value) => InternalResult(value, senderRef)
          case Failure(exception) => InternalError(exception, senderRef)
        }
        Behaviors.same
      case GetAiringsByStartAndDuration(startTime, duration, senderRef) =>
        ctx.pipeToSelf(getAiringsByStartAndDuration(startTime, duration)) {
          case Success(value) => InternalResult(value, senderRef)
          case Failure(exception) => InternalError(exception, senderRef)
        }
        Behaviors.same
      case UpsertAiring(airing, senderRef) =>
        ctx.pipeToSelf(upsertAiring(airing)) {
          case Success(value) => InternalResult(value, senderRef)
          case Failure(exception) => InternalError(exception, senderRef)
        }
        Behaviors.same
      case GetAiringFromGracenote(showId, airing, senderRef) =>
        ctx.pipeToSelf(getAiringFromGracenote(showId, airing)) {
          case Success(value) => InternalResult(value, senderRef)
          case Failure(exception) => InternalError(exception, senderRef)
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
