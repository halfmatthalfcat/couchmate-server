package com.couchmate.external.gracenote.listing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.dao.{AiringDAO, LineupDAO, ShowDAO}
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel, Show}
import com.couchmate.external.gracenote.models.GracenoteAiring

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object LineupDisabler
  extends LineupDAO
  with AiringDAO
  with ShowDAO {
  sealed trait Command

  final case class Disabled(gracenoteAiring: GracenoteAiring) extends Command
  final case class DisableFailure(
    gracenoteAiring: GracenoteAiring,
    err: Throwable
  ) extends Command

  private final case class ShowSuccess(show: Option[Show]) extends Command
  private final case class ShowFailure(err: Throwable) extends Command

  private final case class AiringSuccess(airing: Option[Airing]) extends Command
  private final case class AiringFailure(err: Throwable) extends Command

  private final case class LineupSuccess(lineup: Option[Lineup]) extends Command
  private final case class LineupFailure(err: Throwable) extends Command

  private final case class DisableSuccess(lineup: Lineup) extends Command

  private final case class State(
    show: Option[Show] = None,
    airing: Option[Airing] = None,
    lineup: Option[Lineup] = None
  )

  def apply(
    gracenoteAiring: GracenoteAiring,
    providerChannel: ProviderChannel,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.pipeToSelf(getShowByExt(gracenoteAiring.program.rootId)) {
      case Success(value) => ShowSuccess(value)
      case Failure(exception) => ShowFailure(exception)
    }

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case ShowSuccess(Some(value)) =>
        ctx.pipeToSelf(getAiringByShowStartAndEnd(
          value.extId,
          gracenoteAiring.startTime,
          gracenoteAiring.endTime
        )) {
          case Success(value) => AiringSuccess(value)
          case Failure(exception) => AiringFailure(exception)
        }
        Behaviors.same
      case AiringSuccess(Some(value)) =>
        ctx.pipeToSelf(getLineupForProviderChannelAndAiring(
          providerChannel.providerChannelId.get,
          value.airingId.get
        )) {
          case Success(value) => LineupSuccess(value)
          case Failure(exception) => LineupFailure(exception)
        }
        Behaviors.same
      case LineupSuccess(Some(value)) =>
        ctx.pipeToSelf(upsertLineup(value.copy(active = false))) {
          case Success(value) => DisableSuccess(value)
          case Failure(exception) => DisableFailure(
            gracenoteAiring,
            exception
          )
        }
        Behaviors.same
      case DisableSuccess(_) =>
        senderRef ! Disabled(gracenoteAiring)
        Behaviors.stopped

      case _ => Behaviors.unhandled
    }

    run()
  }
}
