package com.couchmate.services.data

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.cluster.typed.Cluster

import scala.concurrent.ExecutionContext

object ServiceRouter {
  def apply[T](
    name: String,
    key: ServiceKey[T],
    service: Behavior[T],
    instances: Int = 3,
    withRoles: Seq[String] = Seq(),
  ): Behavior[T] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext =
      ctx.executionContext

    val hasRoleFn: String => Boolean =
      Cluster(ctx.system).selfMember.hasRole

    val hasRole: Boolean = withRoles.exists(hasRoleFn)

    if (withRoles.isEmpty || hasRole) {
      for (i <- 1 to instances) {
        ctx.spawn(service, s"$name-$i")
      }
    }

    val pool = Routers
      .group[T](key)
      .withRoundRobinRouting()

    val router = ctx.spawn(pool, s"$name-pool")

    Behaviors.supervise {
      Behaviors.receiveMessage[T] {
        command: T =>
          router ! command
          Behaviors.same
      }
    }.onFailure(SupervisorStrategy.restart.withStopChildren(false))
  }
}
