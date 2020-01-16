package com.couchmate

import akka.actor.ActorSystem

import scala.concurrent.duration._
import akka.util.Timeout
import com.couchmate.api.HttpServer
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContext

object Server {

  implicit val config: Config =
    ConfigFactory.load()

  val environment: String =
    config.getString("environment")

  val akkaConfig: Config =
    config.withOnlyPath("akka")

  implicit val system: ActorSystem = ActorSystem(
    "couchmate-server",
    akkaConfig,
  )

  implicit val executionContext: ExecutionContext =
    system.dispatcher

  implicit val timeout: Timeout = 30 seconds

  def main(args: Array[String]): Unit = {
    Server(environment)
  }

}

case class Server(
  environment: String,
)(
  implicit
  val config: Config,
  val actorSystem: ActorSystem,
  val executionContext: ExecutionContext,
  val timeout: Timeout,
) {
  HttpServer()
}
