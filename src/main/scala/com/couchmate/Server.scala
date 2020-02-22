package com.couchmate

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, SupervisorStrategy}
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.cluster.typed.{Cluster, ClusterSingleton, Join, SingletonActor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.couchmate.api.Routes
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.services.thirdparty.gracenote.listing.{ListingCoordinator, ListingIngestor}
import com.couchmate.services.thirdparty.gracenote.GracenoteService
import com.couchmate.services.thirdparty.gracenote.provider.ProviderIngestor
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Server {
  sealed trait Command
  case class StartFailed(cause: Throwable) extends Command
  case class Started(binding: ServerBinding) extends Command
  case object Stop extends Command

  private[this] def running(binding: ServerBinding, ctx: ActorContext[Command]): Behavior[Command] =
    Behaviors.receiveMessagePartial[Command] {
      case Stop =>
        ctx.log.info(
          "Stopping server http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)
        Behaviors.stopped
    }.receiveSignal {
      case (_, PostStop) =>
        binding.unbind()
        Behaviors.same
    }

  private[this] def starting(wasStopped: Boolean, ctx: ActorContext[Command]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case StartFailed(ex) =>
        throw new RuntimeException("Failed to start server", ex)
      case Started(binding) =>
        ctx.log.info(
          "Server online at http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort,
        )
        if (wasStopped) ctx.self ! Stop
        running(binding, ctx)
      case Stop =>
        starting(wasStopped = true, ctx)
    }
  }

  def apply(host: String, port: Int, config: Config): Behavior[Command] = Behaviors.setup { implicit ctx =>

    implicit val ec: ExecutionContext =
      ctx.executionContext

    val db: Database =
      Database.forConfig("db")

    val database: CMDatabase =
      new CMDatabase(db)

    implicit val system: ActorSystem[Nothing] =
      ctx.system

    val cluster: Cluster = Cluster(system)

    cluster.manager ! Join(cluster.selfMember.address)

    implicit val untypedSystem: ClassicActorSystem =
      ctx.system.toClassic

    // TODO remove this, should already be provided by system/untypedSystem
    implicit val materializer: ActorMaterializer =
      ActorMaterializer()(untypedSystem)

    implicit val timeout: Timeout = 30 seconds

    implicit val gracenoteService: GracenoteService =
      new GracenoteService(config);

    val providerIngestor: ProviderIngestor =
      new ProviderIngestor(
        gracenoteService,
        database,
      )

    val listingIngestor: ListingIngestor =
      new ListingIngestor(
        gracenoteService,
        providerIngestor,
        database,
      )

    val singletonManager: ClusterSingleton = ClusterSingleton(system)

    val listingCoordinator: ActorRef[ListingCoordinator.Command] =
      singletonManager.init(
        SingletonActor(
          Behaviors.supervise(
            ListingCoordinator(listingIngestor),
          ).onFailure[Exception](SupervisorStrategy.restart),
          "ListingCoordinator",
        ),
      )

    val httpServer: Future[Http.ServerBinding] = Http().bindAndHandle(
      Routes(
        providerIngestor,
        listingCoordinator,
        database,
      ),
      interface = host,
      port = port,
    )

    ctx.pipeToSelf(httpServer) {
      case Success(binding) => Started(binding)
      case Failure(ex)      => StartFailed(ex)
    }

    starting(wasStopped = false, ctx)
  }

  def main(args: Array[String]): Unit = {
    val config: Config =
      ConfigFactory.load()

    val system: ActorSystem[Nothing] =
      ActorSystem(
        Server(
          "0.0.0.0",
          8080,
          config,
        ),
        "couchmate",
      )
  }

}
