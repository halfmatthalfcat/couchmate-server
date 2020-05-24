package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.SeriesDAO
import com.couchmate.data.models.Series

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SeriesService extends SeriesDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("series-service")

  sealed trait Command {
    val senderRef: ActorRef[SeriesResult]
  }
  sealed trait SeriesResult
  sealed trait SeriesResultSuccess[T] extends SeriesResult {
    val result: T
  }
  sealed trait SeriesResultFailure extends SeriesResult {
    val err: Throwable
  }

  final case class GetSeries(
    seriesId: Long,
    senderRef: ActorRef[SeriesResult]
  ) extends Command
  final case class GetSeriesSuccess(
    result: Option[Series]
  ) extends SeriesResultSuccess[Option[Series]]
  final case class GetSeriesFailure(
    err: Throwable
  ) extends SeriesResultFailure

  final case class GetSeriesByExt(
    extId: Long,
    senderRef: ActorRef[SeriesResult]
  ) extends Command
  final case class GetSeriesByExtSuccess(
    result: Option[Series]
  ) extends SeriesResultSuccess[Option[Series]]
  final case class GetSeriesByExtFailure(
    err: Throwable
  ) extends SeriesResultFailure

  final case class UpsertSeries(
    series: Series,
    senderRef: ActorRef[SeriesResult]
  ) extends Command
  final case class UpsertSeriesSuccess(
    result: Series
  ) extends SeriesResultSuccess[Series]
  final case class UpsertSeriesFailure(
    err: Throwable
  ) extends SeriesResultFailure

  private final case class InternalSuccess[T](
    result: SeriesResultSuccess[T],
    senderRef: ActorRef[SeriesResult]
  ) extends Command

  private final case class InternalFailure(
    err: SeriesResultFailure,
    senderRef: ActorRef[SeriesResult]
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

      case GetSeries(seriesId, senderRef) =>
        ctx.pipeToSelf(getSeries(seriesId)) {
          case Success(value) => InternalSuccess(GetSeriesSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetSeriesFailure(exception), senderRef)
        }
        Behaviors.same
      case GetSeriesByExt(extId, senderRef) =>
        ctx.pipeToSelf(getSeriesByExt(extId)) {
          case Success(value) => InternalSuccess(GetSeriesByExtSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetSeriesByExtFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertSeries(series, senderRef) =>
        ctx.pipeToSelf(upsertSeries(series)) {
          case Success(value) => InternalSuccess(UpsertSeriesSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertSeriesFailure(exception), senderRef)
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