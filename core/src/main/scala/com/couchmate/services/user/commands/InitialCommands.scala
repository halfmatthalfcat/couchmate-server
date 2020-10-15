package com.couchmate.services.user.commands

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.Effect
import com.couchmate.api.ws.protocol.External
import com.couchmate.common.dao.GridDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.services.GridCoordinator
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser._
import com.couchmate.services.user.context.{GeoContext, RoomContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{PromExtension, RoomExtension, SingletonExtension}

import scala.concurrent.ExecutionContext
import scala.util.Success

object InitialCommands extends GridDAO {
  private[user] def connect(
    userContext: UserContext,
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command],
    roomContext: Option[RoomContext]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    ec: ExecutionContext,
    metrics: PromExtension,
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command],
    room: RoomExtension
  ): Effect[Connected, State] = roomContext.fold(
    Effect
      .persist(Connected(geo, ws))
      .thenRun((_: State) => metrics.incSession(
        userContext.providerId,
        userContext.providerName,
        geo.timezone,
        geo.country,
      ))
      .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.SetSession(
        userContext.getClientUser,
        userContext.providerName,
        userContext.token,
      )))
      .thenRun((_: State) =>
        singletons.gridCoordinator ! GridCoordinator.AddListener(
          userContext.providerId, gridAdapter,
        ),
      )
      .thenRun((_: State) => ctx.pipeToSelf(getGrid(userContext.providerId)) {
        case Success(value) => UpdateGrid(value)
      })
      .thenRun({
        case ConnectedState(_, _, ws) => ctx.watchWith(
          ws,
          Disconnect,
        )
      })
      .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} connected"))
      .thenUnstashAll()
  ) { roomContext =>
    Effect
      .persist(Connected(geo, ws))
      .thenRun((_: State) => metrics.incSession(
        userContext.providerId,
        userContext.providerName,
        geo.timezone,
        geo.country,
      ))
      .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.SetSession(
        userContext.getClientUser,
        userContext.providerName,
        userContext.token,
      )))
      .thenRun((_: State) => room.join(
        roomContext.airingId,
        userContext,
        roomContext.roomId.name
      ))
      .thenRun({
        case ConnectedState(_, _, ws) => ctx.watchWith(
          ws,
          Disconnect,
        )
      })
      .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} connected with previous room ${roomContext.airingId}"))
      .thenUnstashAll()
  }
}
