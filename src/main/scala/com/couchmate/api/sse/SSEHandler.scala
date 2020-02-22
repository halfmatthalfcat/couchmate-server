package com.couchmate.api.sse

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import com.couchmate.Server

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object SSEHandler {
  sealed trait Command
  case object Ack extends Command
  case object Init extends Command
  case object Complete extends Command
  case class Connected(actorRef: ActorRef[ServerSentEvent]) extends Command
  case class Failed(ex: Throwable) extends Command

  def apply(behavior: Behavior[Command])(
    implicit
    ctx: ActorContext[Server.Command],
    executionContext: ExecutionContext,
  ): Source[ServerSentEvent, NotUsed] = {
    val conn: ActorRef[Command] =
      ctx.spawnAnonymous(behavior)

    ActorSource
      .actorRef[ServerSentEvent](
        PartialFunction.empty,
        PartialFunction.empty,
        10,
        OverflowStrategy.dropHead,
      )
      .mapMaterializedValue { outgoingActor: ActorRef[ServerSentEvent] =>
        conn ! Connected(outgoingActor)
        NotUsed
      }
      .keepAlive(1 second, () => ServerSentEvent.heartbeat)
      .watchTermination() { (m, f) =>
        f.onComplete {
          case Success(Done) => conn ! Complete
          case Failure(ex) => conn ! Failed(ex)
        }
        m
      }
  }

  def interceptor[T](behavior: Behavior[T])(handler: PartialFunction[SSEHandler.Command, T]): Behavior[SSEHandler.Command] =
    Behaviors.intercept[SSEHandler.Command, T](
      () => new SSEHandlerInterceptor[T](handler)
    )(behavior)
}
