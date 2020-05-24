package com.couchmate.data.db.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.UserActivityDAO
import com.couchmate.data.models.UserActivity

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserActivityService extends UserActivityDAO {
  val Group: ServiceKey[Command] =
    ServiceKey[Command]("user-activity-service")

  sealed trait Command {
    val senderRef: ActorRef[UserActivityResult]
  }
  sealed trait UserActivityResult
  sealed trait UserActivityResultSuccess[T] extends UserActivityResult {
    val result: T
  }
  sealed trait UserActivityResultFailure extends UserActivityResult {
    val err: Throwable
  }

  final case class AddUserActivity(
    userActivity: UserActivity,
    senderRef: ActorRef[UserActivityResult]
  ) extends Command
  final case class AddUserActivitySuccess(
    result: UserActivity
  ) extends UserActivityResultSuccess[UserActivity]
  final case class AddUserActivityFailure(
    err: Throwable
  ) extends UserActivityResultFailure

  private final case class InternalSuccess[T](
    result: UserActivityResultSuccess[T],
    senderRef: ActorRef[UserActivityResult]
  ) extends Command

  private final case class InternalFailure(
    err: UserActivityResultFailure,
    senderRef: ActorRef[UserActivityResult]
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

      case AddUserActivity(userActivity, senderRef) =>
        ctx.pipeToSelf(addUserActivity(userActivity)) {
          case Success(value) => InternalSuccess(AddUserActivitySuccess(value), senderRef)
          case Failure(exception) => InternalFailure(AddUserActivityFailure(exception), senderRef)
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