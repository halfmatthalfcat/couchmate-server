package com.couchmate

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.Uri
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.couchmate.api.ApiServer
import com.couchmate.util.akka.extensions.{PromExtension, RoomExtension, SingletonExtension}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Server {

  sealed trait Command

  case class StartFailed(cause: Throwable) extends Command

  case class Started(binding: ServerBinding) extends Command

  case object Stop extends Command

  private[this] def running(
    binding: ServerBinding,
    ctx: ActorContext[Command]
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial[Command] {
      case Stop =>
        ctx.log.info(
          "Stopping server http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort,
        )
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

    implicit val system: ActorSystem[Nothing] =
      ctx.system

    if (config.getString("environment") != "local") {
      ClusterBootstrap(system).start()
    } else {
      val cluster: Cluster = Cluster(system)

      cluster.manager ! Join(cluster.selfMember.address)
    }

    RoomExtension(ctx.system)

    SingletonExtension(ctx.system)

    val metrics: PromExtension =
      PromExtension(ctx.system)

    implicit val timeout: Timeout = 30 seconds

    ctx.pipeToSelf(ApiServer(
      host,
      port,
      metrics.registry,
      metrics.settings,
      AkkaManagement(system)
    )) {
      case Success(binding) => Started(binding)
      case Failure(ex) => StartFailed(ex)
    }

    starting(wasStopped = false, ctx)
  }

  def main(args: Array[String]): Unit = {
    val config: Config =
      ConfigFactory.load()

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
