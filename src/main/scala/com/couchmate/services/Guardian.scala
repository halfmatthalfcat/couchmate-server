package com.couchmate.services

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.ExecutionContext

object Guardian {
  def apply(): Behavior[Nothing] = Behaviors
    .setup[Receptionist.Listing] { ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext


      Behaviors.empty
    }.narrow
}
