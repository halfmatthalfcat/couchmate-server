package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import com.couchmate.data.models.User
import com.couchmate.services.ChatLobby
import com.couchmate.services.ChatLobby.JoinRoom

class RoomExtension(system: ActorSystem[_]) extends Extension {
  private[this] val sharding: ClusterSharding =
    ClusterSharding(system)

  private[this] val shardRegion: ActorRef[ShardingEnvelope[ChatLobby.Command]] =
    sharding.init(Entity(ChatLobby.TypeKey)(
      context => ChatLobby(UUID.fromString(context.entityId)),
    ))

  def join(roomId: UUID, user: User, actorRef: ActorRef[ChatLobby.Command]): Unit = {
    shardRegion ! ShardingEnvelope(
      roomId.toString,
      JoinRoom(user, actorRef)
    )
  }

}

object RoomExtension extends ExtensionId[RoomExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): RoomExtension = new RoomExtension(system)
}
