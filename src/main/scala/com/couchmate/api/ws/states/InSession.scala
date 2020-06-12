package com.couchmate.api.ws.states

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.api.models.User
import com.couchmate.api.ws.Commands.{Command, InRoom, Outgoing, PartialCommand}
import com.couchmate.api.ws.protocol.{SetSession, UpdateGrid}
import com.couchmate.api.ws.{GeoContext, SessionContext}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.Chatroom
import com.couchmate.util.akka.extensions.{PromExtension, RoomExtension, SingletonExtension}

class InSession private[ws](
  session: SessionContext,
  geo: GeoContext,
  ctx: ActorContext[Command],
  ws: ActorRef[Command],
) extends BaseState(ctx, ws) {
  val metrics: PromExtension =
    PromExtension(ctx.system)
  val lobby: RoomExtension =
    RoomExtension(ctx.system)

  val gridCoordinator: ActorRef[GridCoordinator.Command] =
    SingletonExtension(ctx.system).gridCoordinator

  val gridAdapter: ActorRef[GridCoordinator.Command] = ctx.messageAdapter {
    case GridUpdate(grid) => Outgoing(UpdateGrid(grid))
  }

  val lobbyAdapter: ActorRef[Chatroom.Command] = ctx.messageAdapter {
    case Chatroom.RoomJoined(roomId) => InRoom.RoomJoined(roomId)
    case Chatroom.RoomParticipants(participants) => InRoom.SetParticipants(participants)
    case Chatroom.ParticipantJoined(participant) => InRoom.AddParticipant(participant)
    case Chatroom.ParticipantLeft(participant) => InRoom.RemoveParticipant(participant)
  }

  ws ! Outgoing(SetSession(
    User(
      userId = session.user.userId.get,
      username = session.userMeta.username,
      email = session.userMeta.email,
      token = session.token
    ),
    session.providerName,
    session.token,
  ))

  metrics.incSession(
    session.providerId,
    session.providerName,
    geo.timezone,
    geo.country,
  )

  gridCoordinator ! GridCoordinator.AddListener(
    session.providerId,
    gridAdapter,
  )

  lobby.join(
    UUID.fromString("00000000-0000-0000-0000-000000000000"),
    session.user.userId.get,
    session.userMeta.username,
    lobbyAdapter
  )

  override protected def internal: PartialCommand = {
    case InRoom.RoomJoined(roomId) =>
      ctx.log.debug(s"Joined room ${roomId.toString}")
      Behaviors.same
    case InRoom.SetParticipants(participants) =>
      ctx.log.debug(s"Setting participants: ${participants.mkString("\n")}")
      Behaviors.same
    case InRoom.AddParticipant(participant) =>
      ctx.log.debug(s"Adding participant: ${participant.username}")
      Behaviors.same
    case InRoom.RemoveParticipant(participant) =>
      ctx.log.debug(s"Removing participant: ${participant.username}")
      Behaviors.same
  }

  override protected def incoming: PartialCommand =
    PartialFunction.empty

  override def onClose(): Unit = {
    gridCoordinator ! GridCoordinator.RemoveListener(
      session.providerId,
      gridAdapter,
    )
  }
}
