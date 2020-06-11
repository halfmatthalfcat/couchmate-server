package com.couchmate.api.ws.states

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.api.models.User
import com.couchmate.api.ws.Commands.{Command, InRoom, Outgoing, PartialCommand}
import com.couchmate.api.ws.protocol.{SetSession, UpdateGrid}
import com.couchmate.api.ws.{GeoContext, SessionContext}
import com.couchmate.services.{Chatroom, GridCoordinator}
import com.couchmate.services.GridCoordinator.GridUpdate
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
    case Chatroom.RoomJoined => InRoom.RoomJoined
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
    UUID.randomUUID(),
    session.user,
    lobbyAdapter
  )

  override protected def internal: PartialCommand =
    PartialFunction.empty

  override protected def incoming: PartialCommand =
    PartialFunction.empty

  override def onClose(): Unit = {
    gridCoordinator ! GridCoordinator.RemoveListener(
      session.providerId,
      gridAdapter,
    )
  }
}
