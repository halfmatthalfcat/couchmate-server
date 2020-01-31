package com.couchmate.api.sse

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.sse.ServerSentEvent

case class SseActor(roomId: Long) extends Actor with ActorLogging {
  override def receive: Receive = {
    case SseActor.Connected(sseActor) =>
      log.info(s"SSE User has connected to room ${roomId}")
      context become connected(sseActor)
  }

  def connected(sseActor: ActorRef): Receive = {
    case SseActor.Complete => log.info("Done")
  }
}

object SseActor {
  case object Ack
  case object Init
  case object Complete
  case class Connected(actorRef: ActorRef)
  case class Failure(ex: Throwable)
}
