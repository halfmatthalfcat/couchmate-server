package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.SportOrganizationDAO
import com.couchmate.data.models.SportOrganization

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SportOrganizationService extends SportOrganizationDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("sport-organization-service")

  sealed trait Command {
    val senderRef: ActorRef[SportOrganizationResult]
  }
  sealed trait SportOrganizationResult
  sealed trait SportOrganizationResultSuccess[T] extends SportOrganizationResult {
    val result: T
  }
  sealed trait SportOrganizationResultFailure extends SportOrganizationResult {
    val err: Throwable
  }

  final case class GetSportOrganization(
    sportOrganizationId: Long,
    senderRef: ActorRef[SportOrganizationResult]
  ) extends Command
  final case class GetSportOrganizationSuccess(
    result: Option[SportOrganization]
  ) extends SportOrganizationResultSuccess[Option[SportOrganization]]
  final case class GetSportOrganizationFailure(
    err: Throwable
  ) extends SportOrganizationResultFailure

  final case class GetSportOrganizationBySportAndOrg(
    extSportId: Long,
    extOrgId: Option[Long],
    senderRef: ActorRef[SportOrganizationResult]
  ) extends Command
  final case class GetSportOrganizationBySportAndOrgSuccess(
    result: Option[SportOrganization]
  ) extends SportOrganizationResultSuccess[Option[SportOrganization]]
  final case class GetSportOrganizationBySportAndOrgFailure(
    err: Throwable
  ) extends SportOrganizationResultFailure

  final case class UpsertSportOrganization(
    sportOrganization: SportOrganization,
    senderRef: ActorRef[SportOrganizationResult]
  ) extends Command
  final case class UpsertSportOrganizationSuccess(
    result: SportOrganization
  ) extends SportOrganizationResultSuccess[SportOrganization]
  final case class UpsertSportOrganizationFailure(
    err: Throwable
  ) extends SportOrganizationResultFailure

  private final case class InternalSuccess[T](
    result: SportOrganizationResultSuccess[T],
    senderRef: ActorRef[SportOrganizationResult]
  ) extends Command

  private final case class InternalFailure(
    err: SportOrganizationResultFailure,
    senderRef: ActorRef[SportOrganizationResult]
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

      case GetSportOrganization(sportOrganizationId, senderRef) =>
        ctx.pipeToSelf(getSportOrganization(sportOrganizationId)) {
          case Success(value) => InternalSuccess(GetSportOrganizationSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetSportOrganizationFailure(exception), senderRef)
        }
        Behaviors.same
      case GetSportOrganizationBySportAndOrg(extSportId, extOrgId, senderRef) =>
        ctx.pipeToSelf(getSportOrganizationBySportAndOrg(extSportId, extOrgId)) {
          case Success(value) => InternalSuccess(GetSportOrganizationBySportAndOrgSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetSportOrganizationBySportAndOrgFailure(exception), senderRef)
        }
        Behaviors.same
      case UpsertSportOrganization(sportOrganization, senderRef) =>
        ctx.pipeToSelf(upsertSportOrganization(sportOrganization)) {
          case Success(value) => InternalSuccess(UpsertSportOrganizationSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(UpsertSportOrganizationFailure(exception), senderRef)
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