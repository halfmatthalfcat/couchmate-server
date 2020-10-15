package com.couchmate.services.room

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.couchmate.common.models.api.room.{Message, MessageMedia, MessageType, Participant, Reaction}

case class RoomMessage(
  messageId: String,
  messageType: MessageType,
  message: Option[String],
  author: Option[Participant],
  recipient: Option[Participant],
  reactions: List[Reaction],
  media: Option[MessageMedia],
  isSelf: Boolean,
)

object RoomMessage {
  def apply(
    messageType: MessageType,
    message: Option[String],
    author: Option[Participant] = None,
    recipient: Option[Participant] = None,
    reactions: List[Reaction] = List.empty,
    media: Option[MessageMedia] = None,
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
      media,
      false
    )
  }

  implicit def toMessage(self: RoomMessage): Message =
    Message(
      self.messageId,
      self.messageType,
      self.message,
      self.author,
      self.recipient,
      self.reactions,
      self.media,
      self.isSelf
    )

  implicit def toMessageCollection(self: List[RoomMessage]): List[Message] =
    self map { a => a: Message }
}

