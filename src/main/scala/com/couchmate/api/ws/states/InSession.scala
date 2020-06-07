package com.couchmate.api.ws.states

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.api.ws.Commands.{Command, Outgoing, PartialCommand}
import com.couchmate.api.ws.protocol.{SetSession, UpdateGrid}
import com.couchmate.api.ws.{GeoContext, SessionContext}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}

class InSession private[ws] (
  session: SessionContext,
  geo: GeoContext,
  ctx: ActorContext[Command],
  ws: ActorRef[Command]
) extends Connected(ctx, ws) {
  val gridCoordinator: ActorRef[GridCoordinator.Command] =
    SingletonExtension(ctx.system).gridCoordinator

  val gridAdapter: ActorRef[GridCoordinator.Command] = ctx.messageAdapter {
    case GridUpdate(grid) => Outgoing(UpdateGrid(grid))
  }

  ws ! Outgoing(SetSession(
    session.userId,
    session.role,
    session.providerName,
    session.token
  ))

  gridCoordinator ! GridCoordinator.AddListener(
    session.providerId,
    gridAdapter
  )

  override protected def internal: PartialCommand =
    PartialFunction.empty

  override protected def incoming: PartialCommand =
    PartialFunction.empty

  override def onClose(): Unit = {
    gridCoordinator ! GridCoordinator.RemoveListener(
      session.providerId,
      gridAdapter
    )
  }
}
