package com.couchmate.services.room

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.couchmate.common.models.api.room.{Message, MessageType, Participant, Reaction}

case class RoomMessage(
  messageId: String,
  messageType: MessageType,
  message: String,
  author: Option[RoomParticipant],
  recipient: Option[RoomParticipant],
  reactions: List[Reaction],
  isSelf: Boolean,
)

object RoomMessage {
  def apply(
    messageType: MessageType,
    message: String,
    author: Option[RoomParticipant] = None,
    recipient: Option[RoomParticipant] = None,
    reactions: List[Reaction] = List.empty,
  ): RoomMessage = {
    val instant: Instant = Instant.now()
    val seconds: Long = instant.getEpochSecond
    val nano: Long = instant.truncatedTo(ChronoUnit.MICROS).getNano

    new RoomMessage(
      BigDecimal(s"$seconds.$nano").underlying.stripTrailingZeros.toPlainString,
      messageType,
      message,
      author,
      recipient,
      reactions,
      false
    )
  }

  implicit def toMessage(self: RoomMessage): Message =
    Message(
      self.messageId,
      self.messageType,
      self.message,
      self.author.map(a => Participant(
        a.userId,
        a.username,
      )),
      self.recipient.map(r => Participant(
        r.userId,
        r.username
      )),
      self.reactions,
      self.isSelf
    )

  implicit def toMessageCollection(self: List[RoomMessage]): List[Message] =
    self map { a => a: Message }
}

