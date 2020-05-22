package com.couchmate.util.stream

import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.couchmate.Server
import com.couchmate.data.wire.{IncomingAmqpMessage, IncomingWSMessage, IncomingWireMessage, OutgoingWSMessage, OutgoingWireMessage, UserRoute, WireMessage}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object WSActor extends LazyLogging {
  sealed trait Command

  case class Incoming(message: IncomingWireMessage) extends Command
  case class Outgoing(message: OutgoingWireMessage) extends Command

  case class Connected(actorRef: ActorRef[Command]) extends Command

  private case object Closed                      extends Command
  private case object Complete                    extends Command
  private case class  ConnFailure(ex: Throwable)  extends Command
  private case class  Failed(ex: Throwable)       extends Command

  private case class  State()

  def apply(userId: UUID): Behavior[Command] = Behaviors.setup { ctx =>
    logger.info(s"Starting WS Actor with userId: ${userId.toString}")

    def run(state: State): Behavior[Command] = Behaviors.receiveMessage {
      case Incoming(IncomingWSMessage(message)) =>
        logger.debug(s"Got WS Message: ${message}")
        Behaviors.same
    }

    run(State())
  }

  def ws(userId: UUID)(
    implicit
    ctx: ActorContext[Server.Command],
    ec: ExecutionContext,
  ): Flow[Message, Message, NotUsed] = {
    val actorRef =
      ctx.spawnAnonymous(apply(userId))

    val incoming = Flow[Message]
      .filter(_.isText)
      .flatMapConcat(_.asTextMessage.getStreamedText)
      .map(text => Try(
        Json.parse(text).as[WireMessage]
      ))
      .collect {
        case Success(msg) => Incoming(
          IncomingWSMessage(msg)
        )
      }
      .to(ActorSink.actorRef(
        actorRef,
        Complete,
        Failed,
      ))

    val outgoing = ActorSource
      .actorRef[Command](
        PartialFunction.empty,
        PartialFunction.empty,
        10,
        OverflowStrategy.dropNew
      ).mapMaterializedValue { outgoingActor: ActorRef[Command] =>
        actorRef ! Connected(outgoingActor)
      }.collect {
        case Outgoing(OutgoingWSMessage(message)) =>
          TextMessage(
            Json.toJson(message).toString
          )
      }.watchTermination() { (m, f) =>
        f.onComplete {
          case Success(Done) =>
            actorRef ! Closed
          case Failure(exception) =>
            actorRef ! ConnFailure(exception)
        }
        m
      }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
