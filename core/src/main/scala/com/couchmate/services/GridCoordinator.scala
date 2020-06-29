package com.couchmate.services

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.common.dao.GridDAO
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.util.akka.extensions.DatabaseExtension
import com.couchmate.common.db.PgProfile.api._

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

  private final case class GridCoordinatorState(
    currentGrid: Option[Grid],
    listeners: Set[ActorRef[Command]]
  )

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(StartUpdate, 5 seconds)

      def run(state: Map[Long, GridCoordinatorState]): Behavior[Command] = Behaviors.receiveMessage {
        case AddListener(providerId, listener) =>
          ctx.watchWith(listener, RemoveListener(providerId, listener))

          val nextState: GridCoordinatorState = state.getOrElse(providerId, GridCoordinatorState(
            None,
            Set.empty
          ))

          if (nextState.currentGrid.nonEmpty) {
            listener ! GridUpdate(nextState.currentGrid.get)
          }

          run(state + (providerId -> nextState.copy(
            listeners = nextState.listeners + listener
          )))
        case RemoveListener(providerId, listener) =>
          val nextState: GridCoordinatorState = state.getOrElse(providerId, GridCoordinatorState(
            None,
            Set.empty
          ))

          run(state + (providerId -> nextState.copy(
            listeners = nextState.listeners - listener
          )))
        case StartUpdate => state.keys.foreach { providerId: Long =>
          ctx.pipeToSelf(getGrid(providerId)) {
            case Success(value) => GridSuccess(value)
            case Failure(exception) => GridFailure(exception)
          }
        }
          Behaviors.same
        case GridSuccess(grid) =>
          val nextState: GridCoordinatorState = state.getOrElse(grid.providerId, GridCoordinatorState(
            Some(grid),
            Set.empty
          ))

          nextState.listeners.foreach { listener =>
            listener ! GridUpdate(grid)
          }

          run(state + (grid.providerId -> nextState))
      }

      run(Map())
    }
  }
}
