package com.couchmate.services.user.commands

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder}
import com.couchmate.api.ws.protocol.{External, Protocol}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{Connected, ConnectedState, Disconnect, State}
import com.couchmate.services.user.context.{GeoContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{PromExtension, SingletonExtension}

object InitialCommands {
  private[user] def connect(
    userContext: UserContext,
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command],
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    metrics: PromExtension,
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command],
  ): Effect[Connected, State] = Effect
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
    .thenRun((s: State) => s match {
      case ConnectedState(_, _, ws) => ctx.watchWith(
        ws,
        Disconnect,
      )
    })
    .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} connected"))
    .thenUnstashAll()
}
