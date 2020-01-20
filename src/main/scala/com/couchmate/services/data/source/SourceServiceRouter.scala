package com.couchmate.services.data.source

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import com.couchmate.data.schema.PgProfile.api._

import scala.concurrent.ExecutionContext

object SourceServiceRouter {
  def apply(
    withRoles: Option[Seq[String]] = None
  )(
    implicit
    db: Database,
  ): Behavior[SourceService.Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext =
      ctx.executionContext

    ctx.spawnAnonymous(SourceService())

    val pool = Routers
      .group(SourceService.SourceServiceKey)
      .withRoundRobinRouting()

    val router = ctx.spawn(pool, "source-service-pool")

    Behaviors.receiveMessage[SourceService.Command] {
      command: SourceService.Command =>
        router ! command
        Behaviors.same
    }

  }
}
