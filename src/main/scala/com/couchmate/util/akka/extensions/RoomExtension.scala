package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.persistence.typed.PersistenceId
import com.couchmate.data.models.User
import com.couchmate.services.room.Chatroom
import com.couchmate.services.room.Chatroom.JoinRoom

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
    roomId: UUID,
    userId: UUID,
    username: String,
    actorRef: ActorRef[Chatroom.Command]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      roomId.toString,
      JoinRoom(userId, username, actorRef)
    )
  }

}

object RoomExtension extends ExtensionId[RoomExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): RoomExtension = new RoomExtension(system)
}
