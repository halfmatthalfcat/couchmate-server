package com.couchmate.util.akka

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{AbruptStageTerminationException, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import akka.{Done, NotUsed}
import com.couchmate.Server
import com.couchmate.util.http.HttpActor
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object WSActor extends LazyLogging {
  def apply[T, F: Format](
    behavior: Behavior[T],
    connected: ActorRef[T] => T,
    complete: T,
    socketComplete: T,
    completeMatcher: PartialFunction[T, Unit],
    failed: Throwable => T,
    incoming: PartialFunction[F, T],
    outgoing: PartialFunction[T, F],
  )(
    implicit
    ctx: ActorContext[HttpActor.Command],
    ec: ExecutionContext,
  ): Flow[Message, Message, NotUsed] = {
    val actorRef: ActorRef[T] =
      ctx.spawnAnonymous(behavior)

    val incomingSink: Sink[Message, NotUsed] = Flow[Message]
      .filter(_.isText)
      .flatMapConcat(_.asTextMessage.getStreamedText)
      .map(text => Try(
        Json.parse(text).as[F]
      ))
      .collect {
        case Success(msg) => msg
      }
      .collect(incoming)
      .to(ActorSink.actorRef(
        actorRef,
        complete,
        failed,
      ))

    val outgoingSource: Source[TextMessage.Strict, Unit] = ActorSource
      .actorRef[T](
        completeMatcher,
        PartialFunction.empty,
        10,
        OverflowStrategy.dropNew
      ).mapMaterializedValue { outgoingActor: ActorRef[T] =>
        actorRef ! connected(outgoingActor)
      }
      .collect(outgoing)
      .map(msg => TextMessage(
        Json.toJson(msg).toString
      ))

    Flow.fromSinkAndSource(incomingSink, outgoingSource)
  }
}
