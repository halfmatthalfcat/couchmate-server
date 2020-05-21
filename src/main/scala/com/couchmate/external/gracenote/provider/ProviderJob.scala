package com.couchmate.external.gracenote.provider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Broadcast, Sink, Source}
import com.couchmate.data.models.Provider
import com.couchmate.api.models.{Provider => ApiProvider}
import com.couchmate.data.db.CMDatabase
import com.couchmate.external.gracenote.GracenoteService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

object ProviderJob extends LazyLogging {
  sealed trait Command
  case class JobEnded(zipCode: String, country: Option[String]) extends Command
  case class AddProvider(provider: ApiProvider) extends Command
  case class AddListener(actorRef: ActorRef[Command]) extends Command

  def apply(
    zipCode: String,
    country: Option[String],
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    logger.debug(s"Starting job $zipCode for ${country.getOrElse("USA")}")
    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    val db: CMDatabase = CMDatabase()
    val gnService: GracenoteService = GracenoteService()
    val providerIngestor: ProviderIngestor =
      new ProviderIngestor(gnService, db)

    providerIngestor
      .ingestProviders(zipCode, country)
      .to(Sink.combine(
        Sink.foreach[Provider](provider => ctx.self ! AddProvider(ApiProvider(
          provider.providerId.get,
          provider.name,
          provider.`type`,
          provider.location,
        ))),
        Sink.onComplete(_ =>
          ctx.self ! JobEnded(zipCode, country)
        ),
      )(Broadcast[Provider](_))).run()

    def run(listeners: Seq[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case AddListener(listener) =>
        run(listeners :+ listener)
      case addProvider: AddProvider =>
        listeners.foreach(_ ! addProvider)
        Behaviors.same
      case jobEnded: JobEnded =>
        listeners.foreach(_ ! jobEnded)
        parent ! jobEnded
        db.close()
        Behaviors.stopped
    }

    run(Seq(initiate))
  }
}
