package com.couchmate.services.user.commands

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder}
import com.couchmate.api.ws.protocol.External
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.room.message.{Authorable, Message}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.room.RoomId
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{Disconnected, RoomJoined, RoomLeft, State, UpdateGrid}
import com.couchmate.services.user.commands.InitialCommands.getGrid
import com.couchmate.services.user.context.{GeoContext, RoomContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{PromExtension, RoomExtension, SingletonExtension}

import scala.concurrent.ExecutionContext
import scala.util.Success

object RoomCommands {
  private[user] def disconnect(
    userContext: UserContext,
    geoContext: GeoContext,
    roomContext: RoomContext
  )(
    implicit
    metrics: PromExtension,
    room: RoomExtension
  ): EffectBuilder[PersistentUser.Disconnected.type, State] = Effect
    .persist(Disconnected)
    .thenRun((_: State) => room.leave(
      roomContext.airingId,
      roomContext.roomId,
      userContext.user.userId.get
    ))
    .thenRun((_: State) => metrics.decSession(
      userContext.providerId,
      userContext.providerName,
      geoContext.timezone,
      geoContext.country
    ))
    .thenRun((_: State) => metrics.decAttendance(
      userContext.providerId,
      userContext.providerName,
      geoContext.timezone,
      geoContext.country
    ))

  private[user] def roomJoined(
    userContext: UserContext,
    geoContext: GeoContext,
    airingId: String,
    roomId: RoomId,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command],
    metrics: PromExtension
  ): Effect[RoomJoined, State] =
    Effect
      .persist(RoomJoined(airingId, roomId))
      .thenRun((_: State) => singletons.gridCoordinator ! GridCoordinator.RemoveListener(
        userContext.providerId, gridAdapter
      ))
      .thenRun((_: State) =>  metrics.incAttendance(
        userContext.providerId,
        userContext.providerName,
        geoContext.timezone,
        geoContext.country
      ))
      .thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
        External.RoomJoined(airingId)
      ))
      .thenUnstashAll()

  private[user] def roomLeft(
    userContext: UserContext,
    geoContext: GeoContext,
    airingId: String,
    roomId: RoomId
  )(
    implicit
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command],
    metrics: PromExtension,
    room: RoomExtension
  ): Effect[RoomLeft.type, State] =
    Effect
      .persist(RoomLeft)
      .thenRun((_: State) => room.leave(
        airingId,
        roomId,
        userContext.user.userId.get
      ))
      .thenRun((_: State) => singletons.gridCoordinator ! GridCoordinator.AddListener(
        userContext.providerId, gridAdapter
      ))
      .thenRun((_: State) =>  metrics.decAttendance(
        userContext.providerId,
        userContext.providerName,
        geoContext.timezone,
        geoContext.country
      ))

  private[user] def roomClosed(
    userContext: UserContext,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    singletons: SingletonExtension,
    ec: ExecutionContext,
    gridAdapter: ActorRef[GridCoordinator.Command]
  ): Effect[Nothing, State] = Effect
    .none
    .thenRun((_: State) =>
      singletons.gridCoordinator ! GridCoordinator.AddListener(
        userContext.providerId, gridAdapter,
      ),
    )
    .thenRun((_: State) => ctx.pipeToSelf(getGrid(userContext.providerId)) {
      case Success(value) => UpdateGrid(value)
    })
    .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(
      External.RoomClosed
    ))

  private[user] def sendMessage(
    userContext: UserContext,
    message: Message,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    singletons: SingletonExtension,
    ec: ExecutionContext,
  ): Effect[Nothing, State] =
    Effect.none.thenRun((_: State) => message match {
      case m: Message with Authorable =>
        if (!userContext.mutes.map(_.userId).contains(m.author.userId)) {
          ws ! WSPersistentActor.OutgoingMessage(
            External.AppendMessage(m)
          )
        }
      case m: Message => ws ! WSPersistentActor.OutgoingMessage(
        External.AppendMessage(m)
      )
    })

  private[user] def updateMessage(
    userContext: UserContext,
    message: Message,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    singletons: SingletonExtension,
    ec: ExecutionContext,
  ): Effect[Nothing, State] =
    Effect.none.thenRun((_: State) => message match {
      case m: Message with Authorable =>
        if (!userContext.mutes.map(_.userId).contains(m.author.userId)) {
          ws ! WSPersistentActor.OutgoingMessage(
            External.UpdateMessage(m)
          )
        }
      case m: Message => ws ! WSPersistentActor.OutgoingMessage(
        External.UpdateMessage(m)
      )
    })
}
