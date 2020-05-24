package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ZipProviderDAO
import com.couchmate.data.models.{Provider, ZipProvider}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ZipProviderService extends ZipProviderDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("zip-provider-service")

  sealed trait Command {
    val senderRef: ActorRef[ZipProviderResult]
  }
  sealed trait ZipProviderResult
  sealed trait ZipProviderResultSuccess[T] extends ZipProviderResult {
    val result: T
  }
  sealed trait ZipProviderResultFailure extends ZipProviderResult {
    val err: Throwable
  }

  final case class GetZipProvidersByZip(
    zipCode: String,
    senderRef: ActorRef[ZipProviderResult]
  ) extends Command
  final case class GetZipProvidersByZipSuccess(
    result: Seq[ZipProvider]
  ) extends ZipProviderResultSuccess[Seq[ZipProvider]]
  final case class GetZipProvidersByZipFailure(
    err: Throwable
  ) extends ZipProviderResultFailure

  final case class GetProvidersForZip(
    zipCode: String,
    senderRef: ActorRef[ZipProviderResult]
  ) extends Command
  final case class GetProvidersForZipSuccess(
    result: Seq[Provider]
  ) extends ZipProviderResultSuccess[Seq[Provider]]
  final case class GetProvidersForZipFailure(
    err: Throwable
  ) extends ZipProviderResultFailure

  final case class GetProviderForProviderAndZip(
    providerId: Long,
    zipCode: String,
    senderRef: ActorRef[ZipProviderResult]
  ) extends Command
  final case class GetProviderForProviderAndZipSuccess(
    result: Option[ZipProvider]
  ) extends ZipProviderResultSuccess[Option[ZipProvider]]
  final case class GetProviderForProviderAndZipFailure(
    err: Throwable
  ) extends ZipProviderResultFailure

  final case class AddZipProvider(
    zipProvider: ZipProvider,
    senderRef: ActorRef[ZipProviderResult]
  ) extends Command
  final case class AddZipProviderSuccess(
    result: ZipProvider
  ) extends ZipProviderResultSuccess[ZipProvider]
  final case class AddZipProviderFailure(
    err: Throwable
  ) extends ZipProviderResultFailure

  final case class GetZipProviderFromGracenote(
    zipCode: String,
    provider: Provider,
    senderRef: ActorRef[ZipProviderResult]
  ) extends Command
  final case class GetZipProviderFromGracenoteSuccess(
    result: ZipProvider
  ) extends ZipProviderResultSuccess[ZipProvider]
  final case class GetZipProviderFromGracenoteFailure(
    err: Throwable
  ) extends ZipProviderResultFailure

  private final case class InternalSuccess[T](
    result: ZipProviderResultSuccess[T],
    senderRef: ActorRef[ZipProviderResult]
  ) extends Command

  private final case class InternalFailure(
    err: ZipProviderResultFailure,
    senderRef: ActorRef[ZipProviderResult]
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

      case GetZipProvidersByZip(zipCode, senderRef) =>
        ctx.pipeToSelf(getZipProvidersByZip(zipCode)) {
          case Success(value) => InternalSuccess(GetZipProvidersByZipSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetZipProvidersByZipFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProvidersForZip(zipCode, senderRef) =>
        ctx.pipeToSelf(getProvidersForZip(zipCode)) {
          case Success(value) => InternalSuccess(GetProvidersForZipSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProvidersForZipFailure(exception), senderRef)
        }
        Behaviors.same
      case GetProviderForProviderAndZip(providerId, zipCode, senderRef) =>
        ctx.pipeToSelf(getProviderForProviderAndZip(providerId, zipCode)) {
          case Success(value) => InternalSuccess(GetProviderForProviderAndZipSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetProviderForProviderAndZipFailure(exception), senderRef)
        }
        Behaviors.same
      case AddZipProvider(zipProvider, senderRef) =>
        ctx.pipeToSelf(addZipProvider(zipProvider)) {
          case Success(value) => InternalSuccess(AddZipProviderSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(AddZipProviderFailure(exception), senderRef)
        }
        Behaviors.same
      case GetZipProviderFromGracenote(zipCode, provider, senderRef) =>
        ctx.pipeToSelf(getZipProviderFromGracenote(zipCode, provider)) {
          case Success(value) => InternalSuccess(GetZipProviderFromGracenoteSuccess(value), senderRef)
          case Failure(exception) => InternalFailure(GetZipProviderFromGracenoteFailure(exception), senderRef)
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