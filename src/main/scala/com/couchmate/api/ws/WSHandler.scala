package com.couchmate.api.ws

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.model.ws.Message
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.couchmate.Server
import play.api.libs.json.{JsValue, Json, Reads}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object WSHandler {
  sealed trait Command
  case class Connected(actorRef: ActorRef[Message]) extends Command
  case class Incoming(js: JsValue) extends Command
  case object Complete extends Command
  case class Failed(ex: Throwable) extends Command

  def apply(behavior: Behavior[Command])(
    implicit
    ctx: ActorContext[Server.Command],
    ec: ExecutionContext,
  ): Flow[Message, Message, NotUsed] = {
    val conn: ActorRef[Command] =
      ctx.spawnAnonymous(behavior)

    val incoming = Flow[Message]
      .filter(_.isText)
      .flatMapConcat(_.asTextMessage.getStreamedText)
      .map(Json.parse)
      .map(Incoming)
      .to(ActorSink.actorRef(
        conn,
        Complete,
        Failed
      ))

    val outgoing = ActorSource
      .actorRef[Message](
        PartialFunction.empty,
        PartialFunction.empty,
        10,
        OverflowStrategy.dropHead,
      )
      .mapMaterializedValue { outgoingActor: ActorRef[Message] =>
        conn ! Connected(outgoingActor)
        NotUsed
      }
      .watchTermination() { (m, f) =>
        f.onComplete {
          case Success(Done) => conn ! Complete
          case Failure(ex) => conn ! Failed(ex)
        }
        m
      }

    // We drain all incoming messages
    // WS used exclusively for streaming out
    Flow.fromSinkAndSource(incoming, outgoing)
  }

  def interceptor[T](behavior: Behavior[T])(handler: PartialFunction[WSHandler.Command, T]): Behavior[WSHandler.Command] =
    Behaviors.intercept[WSHandler.Command, T](
      () => new WSHandlerInterceptor[T](handler)
    )(behavior)

}
