package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.persistence.typed.PersistenceId
import com.couchmate.services.room.{Chatroom, RoomId}
import com.couchmate.services.room.Chatroom.{AddReaction, JoinRoom, LeaveRoom, RemoveReaction, SendMessage}
import com.couchmate.services.user.context.UserContext

class RoomExtension(system: ActorSystem[_]) extends Extension {
  private[this] val sharding: ClusterSharding =
    ClusterSharding(system)

  private[this] val shardRegion: ActorRef[ShardingEnvelope[Chatroom.Command]] =
    sharding.init(Entity(Chatroom.TypeKey)(
      context => Chatroom(
        context.entityId,
        PersistenceId(
          context.entityTypeKey.name,
          context.entityId
        )
      ),
    ))

  def join(
    airingId: String,
    userContext: UserContext,
    hash: String = "general"
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      JoinRoom(userContext, hash)
    )
  }

  def leave(
    airingId: String,
    roomId: RoomId,
    userId: UUID
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      LeaveRoom(
        roomId,
        userId
      )
    )
  }

  def addReaction(
    airingId: String,
    roomId: RoomId,
    userId: UUID,
    messageId: String,
    shortCode: String,
    actorRef: ActorRef[Chatroom.Command]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      AddReaction(
        roomId,
        userId,
        messageId,
        shortCode,
        actorRef
      )
    )
  }

  def removeReaction(
    airingId: String,
    roomId: RoomId,
    userId: UUID,
    messageId: String,
    shortCode: String,
    actorRef: ActorRef[Chatroom.Command]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      RemoveReaction(
        roomId,
        userId,
        messageId,
        shortCode,
        actorRef
      )
    )
  }

  def message(
    airingId: String,
    roomId: RoomId,
    userId: UUID,
    message: String
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
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
