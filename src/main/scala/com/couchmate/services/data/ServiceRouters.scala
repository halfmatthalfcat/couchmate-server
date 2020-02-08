package com.couchmate.services.data

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import scala.concurrent.ExecutionContext

case class ServiceRouters()(
  implicit
  ctx: ActorContext[_],
  ec: ExecutionContext,
) {  }
