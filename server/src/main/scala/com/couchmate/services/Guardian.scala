package com.couchmate.services

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.data.schema.PgProfile.api._

import scala.concurrent.ExecutionContext

object Guardian {
  def apply()(
    implicit
    db: Database,
  ): Behavior[Nothing] = Behaviors
    .setup[Receptionist.Listing] { ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext


      Behaviors.empty
    }.narrow
}
