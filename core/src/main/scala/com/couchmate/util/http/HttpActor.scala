package com.couchmate.util.http

import akka.actor.typed.{ActorSystem, Behavior, PostStop, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http.ServerBinding
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.couchmate.api.ApiServer
import com.couchmate.util.akka.extensions.{CacheExtension, DatabaseExtension, JwtExtension, PromExtension, SingletonExtension, UserExtension}
import com.couchmate.common.db.PgProfile.api._
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object HttpActor {
  sealed trait Command

  case class StartFailed(cause: Throwable) extends Command
  case class Started(binding: ServerBinding, mgmt: AkkaManagement) extends Command
  case object Stop extends Command

  def apply(
    host: String,
    port: Int,
  ): Behavior[Command] = Behaviors.supervise(Behaviors.setup { implicit ctx: ActorContext[Command] =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val system: ActorSystem[Nothing] = ctx.system

    implicit val timeout: Timeout = 30 seconds
    implicit val config: Config = ConfigFactory.load()

    implicit val singletons: SingletonExtension = SingletonExtension(ctx.system)
    implicit val db: Database = DatabaseExtension(ctx.system).db
    implicit val jwt: JwtExtension = JwtExtension(ctx.system)
    implicit val user: UserExtension = UserExtension(ctx.system)
    val metrics: PromExtension = PromExtension(ctx.system)

    val caches = CacheExtension(ctx.system)
    import caches._

    ctx.pipeToSelf(for {
      api <- ApiServer(
        host,
        port,
        metrics.registry,
        metrics.settings
      )
      mgmt = AkkaManagement(system)
      _ <- mgmt.start()
    } yield (api, mgmt)) {
      case Success((api, mgmt)) => Started(api, mgmt)
      case Failure(ex) => StartFailed(ex)
    }

    starting(wasStopped = false, ctx)
  }).onFailure(SupervisorStrategy.restart)

  private[this] def running(
    binding: ServerBinding,
    management: AkkaManagement,
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
        management.stop()
        Behaviors.same
    }

  private[this] def starting(wasStopped: Boolean, ctx: ActorContext[Command]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case StartFailed(ex) =>
        throw new RuntimeException("Failed to start server", ex)
      case Started(binding, mgmt) =>
        ctx.log.info(
          "Server online at http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort,
        )
        if (wasStopped) ctx.self ! Stop
        running(binding, mgmt, ctx)
      case Stop =>
        starting(wasStopped = true, ctx)
    }
  }

}
