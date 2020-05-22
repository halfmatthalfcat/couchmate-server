package com.couchmate.external.gracenote.listing

import java.time.{LocalDateTime, ZoneId}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Sink, Source}
import com.couchmate.api.models.grid.Grid
import com.couchmate.external.gracenote.GracenoteService
import com.couchmate.external.gracenote.provider.ProviderIngestor
import com.couchmate.util.DateUtils
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.util.Success

object ListingJob extends LazyLogging {
  sealed trait Command
  case class JobEnded(providerId: Long, grid: Grid) extends Command
  case class JobProgress(progress: Double) extends Command
  case class AddListener(actorRef: ActorRef[Command]) extends Command

  def apply(
    providerId: Long,
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    logger.debug(s"Starting job $providerId")
    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    val db: CMDatabase = CMDatabase()
    val gnService: GracenoteService = GracenoteService()
    val providerIngestor: ProviderIngestor =
      new ProviderIngestor(gnService, db)
    val listingIngestor: ListingIngestor =
      new ListingIngestor(gnService, providerIngestor, db)

    Source
      .single(providerId)
      .via(listingIngestor.ingestListings(ListingPullType.Initial))
      .to(Sink.foreach { progress =>
        ctx.self ! JobProgress(progress)
      }).run()

    def run(listeners: Seq[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case AddListener(listener) =>
        run(listeners :+ listener)
      case p @ JobProgress(progress) if progress < 1 =>
        listeners.foreach(_ ! p)
        Behaviors.same
      case JobProgress(progress) if progress == 1 =>
        ctx.pipeToSelf(db.grid.getGrid(
          providerId,
          DateUtils.roundNearestHour(LocalDateTime.now(ZoneId.of("UTC"))),
          60,
        )) {
          case Success(grid) => JobEnded(providerId, grid)
        }
        Behaviors.same
      case ended: JobEnded =>
        listeners.foreach(_ ! ended)
        parent ! ended
        db.close()
        Behaviors.stopped
    }

    run(Seq(initiate))
  }
}
