package com.couchmate.services

import java.time.LocalDateTime

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.api.models.grid.Grid
import com.couchmate.util.akka.extensions.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.GridDAO

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GridCoordinator
  extends GridDAO {
  sealed trait Command

  final case class AddListener(providerId: Long, listener: ActorRef[Command]) extends Command
  final case class RemoveListener(providerId: Long, listener: ActorRef[Command]) extends Command

  final case class GridUpdate(grid: Grid) extends Command

  private final case object StartUpdate extends Command

  private final case class GridSuccess(grid: Grid) extends Command
  private final case class GridFailure(err: Throwable) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(StartUpdate, 5 seconds)

      def run(state: Map[Long, Seq[ActorRef[Command]]]): Behavior[Command] = Behaviors.receiveMessage {
        case AddListener(providerId, listener) =>
          run(state + (providerId -> (state.getOrElse(providerId, Seq()) :+ listener)))
        case RemoveListener(providerId, listener) =>
          ctx.log.debug(s"Removing listener ${listener} from ${state.getOrElse(providerId, Seq())}")
          run(state + (providerId -> state.getOrElse(providerId, Seq()).filterNot(_ == listener)))
        case StartUpdate => state.keys.foreach { providerId: Long =>
          ctx.pipeToSelf(getGrid(providerId, LocalDateTime.now(), 60 * 4)) {
            case Success(value) => GridSuccess(value)
            case Failure(exception) => GridFailure(exception)
          }
        }
          Behaviors.same
        case GridSuccess(grid) =>
          state.getOrElse(grid.providerId, Seq()).foreach { listener =>
            listener ! GridUpdate(grid)
          }
          Behaviors.same
      }

      run(Map())
    }
  }
}
