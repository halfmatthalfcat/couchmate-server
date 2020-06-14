package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.persistence.typed.PersistenceId
import com.couchmate.data.models.User
import com.couchmate.services.room.{Chatroom, RoomId}
import com.couchmate.services.room.Chatroom.{JoinRoom, SendMessage}

class RoomExtension(system: ActorSystem[_]) extends Extension {
  private[this] val sharding: ClusterSharding =
    ClusterSharding(system)

  private[this] val shardRegion: ActorRef[ShardingEnvelope[Chatroom.Command]] =
    sharding.init(Entity(Chatroom.TypeKey)(
      context => Chatroom(
        UUID.fromString(context.entityId),
        PersistenceId(
          context.entityTypeKey.name,
          context.entityId
        )
      ),
    ))

  def join(
    airingId: UUID,
    userId: UUID,
    username: String,
    actorRef: ActorRef[Chatroom.Command]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId.toString,
      JoinRoom(userId, username, actorRef)
    )
  }

  def message(
    airingId: UUID,
    roomId: RoomId,
    userId: UUID,
    message: String
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId.toString,
      SendMessage(
        roomId,
        userId,
        message
      )
    )
  }

}

object RoomExtension extends ExtensionId[RoomExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): RoomExtension = new RoomExtension(system)
}
