package com.couchmate

import java.time.LocalDateTime

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.couchmate.api.Routes
import com.couchmate.common.models.Airing
import com.couchmate.data.db.{AiringDAO, ProviderDAO, ProviderOwnerDAO}
import com.typesafe.config.{Config, ConfigFactory}
import io.getquill.{PostgresJdbcContext, SnakeCase}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait ServerCommands {
  sealed trait Command
  case class StartFailed(cause: Throwable) extends Command
  case class Started(binding: ServerBinding) extends Command
  case object Stop extends Command
}

object Server extends ServerCommands {

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

    implicit val db: PostgresJdbcContext[SnakeCase.type] =
      new PostgresJdbcContext(
        SnakeCase,
        "ctx"
      )

    implicit val ec: ExecutionContext =
      ctx.executionContext

    implicit val system: ActorSystem[Nothing] =
      ctx.system

    implicit val untypedSystem: ClassicActorSystem =
      ctx.system.toClassic

    // TODO remove this, should already be provided by system/untypedSystem
    implicit val materializer: ActorMaterializer =
      ActorMaterializer()(untypedSystem)

    implicit val timeout: Timeout = 30 seconds

    val providerOwnerDAO: ProviderOwnerDAO =
      new ProviderOwnerDAO

    val providerDAO: ProviderDAO =
      new ProviderDAO

//    implicit val gracenoteService: GracenoteService =
//      new GracenoteService(config);

    val httpServer: Future[Http.ServerBinding] = Http().bindAndHandle(
      Routes(
        providerDAO,
        providerOwnerDAO,
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
