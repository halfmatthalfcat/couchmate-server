package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.SportEventDAO
import com.couchmate.data.models.SportEvent

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SportEventService extends SportEventDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("sport-event-service")

  sealed trait Command {
    val senderRef: ActorRef[SportEventResult]
  }
  sealed trait SportEventResult
  sealed trait SportEventResultSuccess[T] extends SportEventResult {
    val result: T
  }
  sealed trait SportEventResultFailure extends SportEventResult {
    val err: Throwable
  }

  final case class GetSportEvent(
    sportEventId: Long,
    senderRef: ActorRef[SportEventResult]
  ) extends Command
  final case class GetSportEventSuccess(
    result: Option[SportEvent]
  ) extends SportEventResultSuccess[Option[SportEvent]]
  final case class GetSportEventFailure(
    err: Throwable
  ) extends SportEventResultFailure

  final case class GetSportEventByNameAndOrg(
    name: String,
    orgId: Long,
    senderRef: ActorRef[SportEventResult]
  ) extends Command
  final case class GetSportEventByNameAndOrgSuccess(
    result: Option[SportEvent]
  ) extends SportEventResultSuccess[Option[SportEvent]]
  final case class GetSportEventByNameAndOrgFailure(
    err: Throwable
  ) extends SportEventResultFailure

  final case class UpsertSportEvent(
    sportEvent: SportEvent,
    senderRef: ActorRef[SportEventResult]
  ) extends Command
  final case class UpsertSportEventSuccess(
    result: SportEvent
  ) extends SportEventResultSuccess[SportEvent]
  final case class UpsertSportEventFailure(
    err: Throwable
  ) extends SportEventResultFailure

  private final case class InternalSuccess[T](
    result: SportEventResultSuccess[T],
    senderRef: ActorRef[SportEventResult]
  ) extends Command

  private final case class InternalFailure(
    err: SportEventResultFailure,
    senderRef: ActorRef[SportEventResult]
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

      case GetSportEvent(sportEventId, senderRef) =>
        ctx.pipeToSelf(getSportEvent(sportEventId)) {
          case Success(value) => InternalSuccess(GetSportEventSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetSportEventFailure(exception), senderRef)
        }
        Behaviors.same
      case GetSportEventByNameAndOrg(name, orgId, senderRef) =>
        ctx.pipeToSelf(getSportEventByNameAndOrg(name, orgId)) {
          case Success(value) => InternalSuccess(GetSportEventByNameAndOrgSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetSportEventByNameAndOrgFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertSportEvent(sportEvent, senderRef) =>
        ctx.pipeToSelf(upsertSportEvent(sportEvent)) {
          case Success(value) => InternalSuccess(UpsertSportEventSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertSportEventFailure(exception), senderRef)
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