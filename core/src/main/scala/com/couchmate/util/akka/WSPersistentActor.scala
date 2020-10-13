package com.couchmate.util.akka

import java.util.UUID

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.couchmate.Server
import com.couchmate.api.ws.protocol.External.Disconnect
import com.couchmate.api.ws.protocol.Protocol
import com.couchmate.services.user.context.{GeoContext, UserContext}
import com.couchmate.util.akka.extensions.UserExtension
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json

import scala.util.{Success, Try}

object WSPersistentActor extends LazyLogging {
  sealed trait Command
  final case class SetUser(userId: UUID) extends Command
  final case class OutgoingMessage(protocol: Protocol) extends Command
  private final case class SetSocket(socket: ActorRef[Command]) extends Command
  private final case class IncomingMessage(protocol: Protocol) extends Command

  private final case object Terminate extends Command
  private final case class TerminateWithError(ex: Throwable) extends Command

  /**
   * Switchboard acts as an intermediary between the raw socket actor
   * and the persistent user actor. This is primarily used for instances
   * where a user (id) context changes and we need to forward messages
   * to a different persistent user than what was originally created.
   */
  private[this] def switchboard(
    currentUserId: UUID,
    geoContext: GeoContext,
    userExtension: UserExtension,
    socket: Option[ActorRef[Command]]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case SetUser(userId) =>
        userExtension.connect(userId, geoContext, ctx.self)
        switchboard(userId, geoContext, userExtension, socket)
      case SetSocket(socket) =>
        ctx.watchWith(socket, Terminate)
        userExtension.connect(currentUserId, geoContext, ctx.self)
        switchboard(currentUserId, geoContext, userExtension, Some(socket))
      case IncomingMessage(protocol: Protocol) =>
        userExtension.incomingMessage(currentUserId, protocol)
        Behaviors.same
      case outgoing: OutgoingMessage if socket.nonEmpty =>
        socket.get ! outgoing
        Behaviors.same
      case Terminate =>
        userExtension.disconnect(currentUserId)
        Behaviors.stopped
      case TerminateWithError(ex) =>
        ctx.log.error(s"WS Terminating with error", ex)
        userExtension.disconnect(currentUserId)
        Behaviors.stopped
    }
  }

  def apply(
    userId: UUID,
    geo: GeoContext,
    userExtension: UserExtension,
    ctx: ActorContext[Server.Command]
  ): Flow[Message, Message, NotUsed] = {
    val sb: ActorRef[Command] =
      ctx.spawn(
        switchboard(userId, geo, userExtension, Option.empty),
        s"$userId-sb"
      )
    val incomingSink: Sink[Message, NotUsed] = Flow[Message]
      .filter(_.isText)
      .flatMapConcat(_.asTextMessage.getStreamedText)
      .map(text => Try(
        Json.parse(text).as[Protocol]
      ))
      .collect {
        case Success(value) => IncomingMessage(value)
      }
      .to(ActorSink.actorRef[Command](
        sb,
        Terminate,
        TerminateWithError,
      ))

    val outgoingSource: Source[TextMessage.Strict, Unit] =
      ActorSource.actorRef[Command](
        { case Terminate => },
        PartialFunction.empty,
        10,
        OverflowStrategy.dropNew,
      ).mapMaterializedValue(sb ! SetSocket(_))
       .collect { case OutgoingMessage(message) => message }
       .map(msg => TextMessage(
        Json.toJson(msg).toString
      ))

    Flow.fromSinkAndSource(
      incomingSink,
      outgoingSource
    )
  }
}
