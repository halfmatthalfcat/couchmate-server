package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.persistence.typed.PersistenceId
import com.couchmate.api.ws.protocol.Protocol
import com.couchmate.services.room.Chatroom
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{Disconnect, RoomMessage, WSMessage}
import com.couchmate.services.user.context.{DeviceContext, GeoContext}
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
    device: Option[DeviceContext],
    ws: ActorRef[WSPersistentActor.Command]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      userId.toString,
      PersistentUser.Connect(geo, device, ws)
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

  def roomMessage(
    userId: UUID,
    message: Chatroom.Command
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      userId.toString,
      RoomMessage(message)
    )
  }

  def inClusterMessage(
    userId: UUID,
    message: PersistentUser.Command
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      userId.toString,
      message
    )
  }
}

object UserExtension extends ExtensionId[UserExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): UserExtension = new UserExtension(system)
}
