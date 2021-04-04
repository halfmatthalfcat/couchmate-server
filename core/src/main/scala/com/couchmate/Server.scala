package com.couchmate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.{Cluster, Join}
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.couchmate.services.cache.ClusterCacheBuster
import com.couchmate.util.http.HttpActor
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon

object Server {
  sealed trait Command

  def apply(host: String, port: Int)(
    implicit
    config: Config
  ): Behavior[Command] = Behaviors.setup { implicit ctx =>

    implicit val system: ActorSystem[Nothing] =
      ctx.system

    if (config.getString("environment") != "local") {
      ClusterBootstrap(system).start()
    } else {
      val cluster: Cluster = Cluster(system)

      cluster.manager ! Join(cluster.selfMember.address)
    }

    ctx.spawn(HttpActor(
      host, port
    ), "http")

    ctx.spawn(
      ClusterCacheBuster(),
      name = "ClusterCacheBuster"
    )

    Behaviors.empty
  }

  def main(args: Array[String]): Unit = {
    Kamon.init()

    implicit val config: Config =
      ConfigFactory.load()

    ActorSystem(
      Server(
        "0.0.0.0",
        8080,
      ),
      "couchmate",
    )
  }

}
