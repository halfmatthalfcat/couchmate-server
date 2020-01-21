package com.couchmate.services.data

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.services.data.source.SourceService

import scala.concurrent.ExecutionContext

case class ServiceRouters()(
  implicit
  ctx: ActorContext[_],
  ec: ExecutionContext,
  db: Database,
) {
  lazy val sourceServiceRouter: ActorRef[SourceService.Command] =
    ctx.spawn(ServiceRouter(
      "source-service",
      SourceService.SourceServiceKey,
      SourceService(),
    ), "source-service-router")
}
