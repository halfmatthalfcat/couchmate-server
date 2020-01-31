package com.couchmate.services.data

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.couchmate.data.schema.PgProfile.api._

import scala.concurrent.ExecutionContext

case class ServiceRouters()(
  implicit
  ctx: ActorContext[_],
  ec: ExecutionContext,
  session: SlickSession,
) {  }
