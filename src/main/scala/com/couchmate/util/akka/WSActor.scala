package com.couchmate.util.akka

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import akka.{Done, NotUsed}
import com.couchmate.Server
import com.couchmate.data.wire.{IncomingWSMessage, OutgoingWSMessage, WireMessage}
import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object WSActor {
  def apply[T](
    behavior: Behavior[T],
    connected: ActorRef[OutgoingWSMessage] => T,
    incoming: IncomingWSMessage => T,
    complete: T,
    failed: Throwable => T,
  )(
    implicit
    ctx: ActorContext[Server.Command],
    ec: ExecutionContext,
  ): Flow[Message, Message, NotUsed] = {
    val actorRef: ActorRef[T] =
      ctx.spawnAnonymous(behavior)

    val incomingSink: Sink[Message, NotUsed] = Flow[Message]
      .filter(_.isText)
      .flatMapConcat(_.asTextMessage.getStreamedText)
      .map(text => Try(
        Json.parse(text).as[WireMessage]
      ))
      .collect {
        case Success(msg) => incoming(IncomingWSMessage(msg))
      }
      .to(ActorSink.actorRef(
        actorRef,
        complete,
        failed,
      ))

    val outgoingSource: Source[TextMessage.Strict, Unit] = ActorSource
      .actorRef[OutgoingWSMessage](
        PartialFunction.empty,
        PartialFunction.empty,
        10,
        OverflowStrategy.dropNew
      ).mapMaterializedValue { outgoingActor: ActorRef[OutgoingWSMessage] =>
        actorRef ! connected(outgoingActor)
      }.collect {
        case OutgoingWSMessage(message) => TextMessage(
          Json.toJson(message).toString
        )
      }.watchTermination() { (m, f) =>
        f.onComplete {
          case Success(Done) =>
            actorRef ! complete
          case Failure(exception) =>
            actorRef ! failed(exception)
        }
        m
      }

    Flow.fromSinkAndSource(incomingSink, outgoingSource)
  }
}
