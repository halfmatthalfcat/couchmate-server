package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.persistence.typed.PersistenceId
import com.couchmate.common.models.api.room.message.TextMessageWithLinks
import com.couchmate.services.room.{Chatroom, RoomId}
import com.couchmate.services.room.Chatroom.{AddReaction, ChangeHashRoom, JoinRoom, LeaveRoom, RemoveReaction, SendGif, SendMessage, SendTextMessageWithLinks}
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
    ).withStopMessage(Chatroom.CloseRoom))

  def join(
    airingId: String,
    userContext: UserContext,
    hash: Option[String]
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      JoinRoom(userContext, hash)
    )
  }

  def changeHash(
    airingId: String,
    userId: UUID,
    hash: String
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      ChangeHashRoom(userId, hash)
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
    shortCode: String
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      AddReaction(
        roomId,
        userId,
        messageId,
        shortCode
      )
    )
  }

  def removeReaction(
    airingId: String,
    roomId: RoomId,
    userId: UUID,
    messageId: String,
    shortCode: String
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      RemoveReaction(
        roomId,
        userId,
        messageId,
        shortCode
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

  def gif(
    airingId: String,
    roomId: RoomId,
    userId: UUID,
    url: String
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      SendGif(
        roomId,
        userId,
        url
      )
    )
  }

  def linkMessage(
    airingId: String,
    roomId: RoomId,
    message: TextMessageWithLinks
  ): Unit = {
    shardRegion ! ShardingEnvelope(
      airingId,
      SendTextMessageWithLinks(
        roomId,
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
