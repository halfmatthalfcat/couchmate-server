package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.persistence.typed.PersistenceId
import com.couchmate.api.ws.protocol.Protocol
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{Disconnect, WSMessage}
import com.couchmate.services.user.context.GeoContext
import com.couchmate.util.akka.WSPersistentActor

class UserExtension(system: ActorSystem[_]) extends Extension {
  private[this] val sharding: ClusterSharding =
    ClusterSharding(system)

  private[this] val shardRegion: ActorRef[ShardingEnvelope[PersistentUser.Command]] =
    sharding.init(Entity(PersistentUser.TypeKey)(
      context => PersistentUser(
        UUID.fromString(context.entityId),
        PersistenceId(
          context.entityTypeKey.name,
          context.entityId
        )
      )
    ).withStopMessage(PersistentUser.Disconnect))

  def connect(
    userId: UUID,
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      userId.toString,
      PersistentUser.Connect(geo, ws)
    )
  }

  def disconnect(userId: UUID): Unit = {
    shardRegion ! ShardingEnvelope(
      userId.toString,
      Disconnect
    )
  }

  def incomingMessage(
    userId: UUID,
    message: Protocol
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      userId.toString,
      WSMessage(message)
    )
  }
}

object UserExtension extends ExtensionId[UserExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): UserExtension = new UserExtension(system)
}
