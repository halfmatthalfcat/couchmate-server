package com.couchmate.services.user.commands

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.Effect
import com.couchmate.api.ws.protocol.External
import com.couchmate.common.dao.{GridDAO, UserActivityDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{UserActivity, UserActivityType}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser._
import com.couchmate.services.user.context.{DeviceContext, GeoContext, RoomContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{PromExtension, RoomExtension, SingletonExtension}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object InitialCommands
  extends UserActivityDAO {

  private[user] def connect(
    userContext: UserContext,
    geo: GeoContext,
    device: Option[DeviceContext],
    ws: ActorRef[WSPersistentActor.Command],
    roomContext: Option[RoomContext]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
    metrics: PromExtension,
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command],
    room: RoomExtension
  ): Effect[Connected, State] = roomContext.fold(
    Effect
      .persist(Connected(userContext, geo, device, ws))
      .thenRun((_: State) => metrics.incSession(
        userContext.providerId,
        userContext.providerName,
        geo.timezone,
        geo.country,
      ))
      .thenRun((_: State) => addUserActivity(UserActivity(
        userId = userContext.user.userId.get,
        action = UserActivityType.Login,
        os = device.flatMap(_.os),
        osVersion = device.flatMap(_.osVersion),
        brand = device.flatMap(_.brand),
        model = device.flatMap(_.model),
        deviceId = device.map(_.deviceId)
      )))
      .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.SetSession(
        userContext.getClientUser(device.map(_.deviceId)),
        userContext.providerName,
        userContext.token,
      )))
      .thenRun((_: State) =>
        singletons.gridCoordinator ! GridCoordinator.AddListener(
          userContext.providerId, gridAdapter,
        ),
      )
      .thenRun((_: State) => ctx.pipeToSelf(GridDAO.getGrid(userContext.providerId)()) {
        case Success(value) => UpdateGrid(value)
        case Failure(exception) => UpdateGridFailed(exception)
      })
      .thenRun({
        case ConnectedState(_, _, _, ws) => ctx.watchWith(
          ws,
          Disconnect,
        )
      })
      .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} connected"))
      .thenUnstashAll()
  ) { roomContext =>
    Effect
      .persist(Connected(userContext, geo, device, ws))
      .thenRun((_: State) => metrics.incSession(
        userContext.providerId,
        userContext.providerName,
        geo.timezone,
        geo.country,
      ))
      .thenRun((_: State) => addUserActivity(UserActivity(
        userId = userContext.user.userId.get,
        action = UserActivityType.Login,
        os = device.flatMap(_.os),
        osVersion = device.flatMap(_.osVersion),
        brand = device.flatMap(_.brand),
        model = device.flatMap(_.model),
        deviceId = device.map(_.deviceId)
      )))
      .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.SetSession(
        userContext.getClientUser(device.map(_.deviceId)),
        userContext.providerName,
        userContext.token,
      )))
      .thenRun((_: State) => room.join(
        roomContext.airingId,
        userContext,
        Some(roomContext.roomId.name)
      ))
      .thenRun({
        case ConnectedState(_, _, _, ws) => ctx.watchWith(
          ws,
          Disconnect,
        )
      })
      .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} connected with previous room ${roomContext.airingId}"))
      .thenUnstashAll()
  }
}
