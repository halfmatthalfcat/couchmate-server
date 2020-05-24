package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.dao.EpisodeDAO
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.models.Episode

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object EpisodeService extends EpisodeDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("episode-service")

  sealed trait Command {
    val senderRef: ActorRef[EpisodeResult]
  }
  sealed trait EpisodeResult
  sealed trait EpisodeResultSuccess[T] extends EpisodeResult {
    val result: T
  }
  sealed trait EpisodeResultFailure extends EpisodeResult {
    val err: Throwable
  }

  final case class GetEpisode(
    episodeId: Long,
    senderRef: ActorRef[EpisodeResult]
  ) extends Command
  final case class GetEpisodeSuccess(
    result: Option[Episode]
  ) extends EpisodeResultSuccess[Option[Episode]]
  final case class GetEpisodeFailure(
    err: Throwable
  ) extends EpisodeResultFailure

  final case class UpsertEpisode(
    episode: Episode,
    senderRef: ActorRef[EpisodeResult]
  ) extends Command
  final case class UpsertEpisodeSuccess(
    result: Episode
  ) extends EpisodeResultSuccess[Episode]
  final case class UpsertEpisodeFailure(
    err: Throwable
  ) extends EpisodeResultFailure

  private final case class InternalSuccess[T](
    result: EpisodeResultSuccess[T],
    senderRef: ActorRef[EpisodeResult]
  ) extends Command
  private final case class InternalFailure(
    err: EpisodeResultFailure,
    senderRef: ActorRef[EpisodeResult]
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

      case GetEpisode(episodeId, senderRef) =>
        ctx.pipeToSelf(getEpisode(episodeId)) {
          case Success(value) => InternalSuccess(GetEpisodeSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetEpisodeFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertEpisode(episode, senderRef) =>
        ctx.pipeToSelf(upsertEpisode(episode)) {
          case Success(value) => InternalSuccess(UpsertEpisodeSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertEpisodeFailure(exception), senderRef)
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
