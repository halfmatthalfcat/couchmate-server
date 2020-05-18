package com.couchmate.api.ws

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.Message
import com.couchmate.api.ws.ProviderHandler.{Connected, Stop}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.JsValue

object SeatHandler extends LazyLogging {

  sealed trait Command
  case class Connected(actorRef: ActorRef[Message]) extends Command
  case class Incoming(js: JsValue) extends Command

  case object Stop extends Command

  def apply(): Behavior[Command] = Behaviors.receiveMessagePartial {
    case Incoming(js: JsValue) =>
      logger.debug(s"Got json: ${js.toString}")
      Behaviors.same
  }

  def ws(): Behavior[WSHandler.Command] =
    WSHandler.interceptor(apply()) {
      case WSHandler.Connected(actorRef) => Connected(actorRef)
      case WSHandler.Complete => Stop
      case WSHandler.Incoming(js) => Incoming(js)
    }
}
