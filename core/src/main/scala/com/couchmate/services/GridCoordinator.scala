package com.couchmate.services

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.common.dao.GridDAO
import com.couchmate.common.models.api.grid.{Grid, GridAiringDynamic, GridDynamic}
import com.couchmate.util.akka.extensions.{CacheExtension, DatabaseExtension}
import com.couchmate.common.db.PgProfile.api._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GridCoordinator {
  sealed trait Command

  final case class AddListener(providerId: Long, listener: ActorRef[Command]) extends Command
  final case class RemoveListener(providerId: Long, listener: ActorRef[Command]) extends Command
  final case class SwapListener(fromId: Long, toId: Long, listener: ActorRef[Command]) extends Command

  final case class GridUpdate(grid: Grid) extends Command
  final case class GridDynamicUpdate(updates: GridDynamic) extends Command

  private final case object StartUpdate extends Command
  private final case class  GridSuccess(providerId: Long, grid: Grid) extends Command
  private final case class  GridFailure(providerId: Long, err: Throwable) extends Command

  private final case object StartDynamicUpdate extends Command
  private final case class  GridDynamicSuccess(providerId: Long, updates: GridDynamic) extends Command
  private final case class  GridDynamicFailure(providerId: Long, err: Throwable) extends Command

  private final case class GridCoordinatorState(
    currentGrid: Option[Grid],
    currentDynamic: Option[GridDynamic],
    listeners: Set[ActorRef[Command]]
  )

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    val caches = CacheExtension(ctx.system)
    import caches._

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(StartDynamicUpdate, 5 seconds)
      timers.startTimerAtFixedRate(StartUpdate, 1 minute)

      def run(state: Map[Long, GridCoordinatorState]): Behavior[Command] = Behaviors.receiveMessage {
        case AddListener(providerId, listener) =>
          ctx.watchWith(listener, RemoveListener(providerId, listener))

          val nextState: GridCoordinatorState = state.getOrElse(providerId, GridCoordinatorState(
            None,
            None,
            Set.empty
          ))

          if (nextState.currentGrid.nonEmpty) {
            listener ! GridUpdate(nextState.currentGrid.get)
          }
          if (nextState.currentDynamic.nonEmpty) {
            listener ! GridDynamicUpdate(nextState.currentDynamic.get)
          }

          run(state + (providerId -> nextState.copy(
            listeners = nextState.listeners + listener
          )))
        case RemoveListener(providerId, listener) =>
          val nextState: GridCoordinatorState = state.getOrElse(providerId, GridCoordinatorState(
            None,
            None,
            Set.empty
          ))
          val nextListeners: Set[ActorRef[Command]] = nextState.listeners - listener;

          if (nextListeners.nonEmpty) {
            run(state + (providerId -> nextState.copy(
              listeners = nextListeners
            )))
          } else {
            run(state - providerId);
          }
        case SwapListener(fromId, toId, listener) =>
          ctx.unwatch(listener)
          ctx.watchWith(listener, RemoveListener(toId, listener))

          val fromState: GridCoordinatorState = state.getOrElse(fromId, GridCoordinatorState(
            None,
            None,
            Set.empty
          ))
          val toState: GridCoordinatorState = state.getOrElse(toId, GridCoordinatorState(
            None,
            None,
            Set.empty
          ))

          if (toState.currentGrid.nonEmpty) {
            listener ! GridUpdate(toState.currentGrid.get)
          }
          if (toState.currentDynamic.nonEmpty) {
            listener ! GridDynamicUpdate(toState.currentDynamic.get)
          }

          run(state + (fromId -> fromState.copy(
            listeners = fromState.listeners - listener
          )) + (toId -> toState.copy(
            listeners = toState.listeners + listener
          )))
        case StartDynamicUpdate => state.keys.foreach { providerId: Long =>
          ctx.pipeToSelf(GridDAO.getGridDynamic(providerId)(bust = true)) {
            case Success(value) => GridDynamicSuccess(providerId, value)
            case Failure(exception) => GridDynamicFailure(providerId, exception)
          }
        }
          Behaviors.same
        case StartUpdate => state.keys.foreach { providerId: Long =>
          ctx.pipeToSelf(GridDAO.getGrid(providerId)()) {
            case Success(value) => GridSuccess(providerId, value)
            case Failure(exception) => GridFailure(providerId, exception)
          }
        }
          Behaviors.same
        case GridSuccess(providerId, grid) =>
          ctx.log.debug(s"Sending grid update for ${providerId}")
          val nextState: GridCoordinatorState = state.getOrElse(providerId, GridCoordinatorState(
            Some(grid),
            None,
            Set.empty
          ))

          nextState.listeners.foreach { listener =>
            listener ! GridUpdate(grid)
          }

          run(state + (providerId -> nextState.copy(
            currentGrid = Some(grid)
          )))
        case GridDynamicSuccess(providerId, updates) =>
          ctx.log.debug(s"Sending grid dynamic updates for $providerId")
          val nextState: GridCoordinatorState = state.getOrElse(providerId, GridCoordinatorState(
            None,
            Some(updates),
            Set.empty
          ))

          nextState.listeners.foreach { listener =>
            listener ! GridDynamicUpdate(updates)
          }

          run(state + (providerId -> nextState.copy(
            currentDynamic = Some(updates)
          )))
        case GridFailure(providerId, err) =>
          ctx.log.error(s"Failed to get grid for provider $providerId", err)
          Behaviors.same
        case GridDynamicFailure(providerId, err) =>
          ctx.log.error(s"Failed to get grid dynamic for provider $providerId", err)
          Behaviors.same
      }

      run(Map())
    }
  }
}
