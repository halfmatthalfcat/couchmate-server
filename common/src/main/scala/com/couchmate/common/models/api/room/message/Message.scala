package com.couchmate.common.models.api.room.message

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import com.couchmate.common.models.api.room.{Participant, Reaction}
import julienrf.json.derived
import play.api.libs.json.{Format, Json, __}

sealed trait Message {
  val messageId: String
}

object Message {
  implicit val format: Format[Message] =
    derived.flat.oformat((__ \ "mtype").format[String])

  def generateId: String = {
    val instant: Instant = Instant.now()
    val seconds: Long = instant.getEpochSecond
    val nano: Long = instant.truncatedTo(ChronoUnit.MICROS).getNano

    BigDecimal(s"$seconds.$nano")
      .underlying
      .stripTrailingZeros
      .toPlainString
  }
}

trait Authorable {
  val author: Participant
  val isSelf: Boolean

  def setSelf(isSelf: Boolean): Message with Authorable
}

trait Reactable {
  val reactions: List[Reaction]

  def addReaction(userId: UUID, shortCode: String): Message with Reactable
  def removeReaction(userId: UUID, shortCode: String): Message with Reactable
}

trait Editable {
  val message: String
  val references: List[MessageReference]

  def setReferences(references: List[MessageReference]): Message with Editable
}

case class SystemMessage(
  messageId: String,
  message: String
) extends Message

object SystemMessage {
  def apply(message: String): SystemMessage =
    new SystemMessage(
      Message.generateId,
      message
    )
}

case class TextMessage(
  messageId: String,
  message: String,
  author: Participant,
  reactions: List[Reaction],
  references: List[MessageReference],
  isSelf: Boolean
) extends Message
  with Editable
  with Authorable
  with Reactable {

  override def setSelf(
    isSelf: Boolean,
  ): Message with Authorable = this.copy(
    isSelf = isSelf
  )

  override def setReferences(
    references: List[MessageReference],
  ): Message with Editable = this.copy(
    references = references
  )

  override def addReaction(
    userId: UUID, shortCode: String,
  ): TextMessage = {
    this.reactions.find(_.shortCode == shortCode).fold(
      this.copy(reactions = reactions :+ Reaction(
        shortCode = shortCode,
        reactors = Seq(userId)
      ))
    )(reaction => this.copy(
      reactions = reactions.updated(
        reactions.indexOf(reaction),
        reaction.copy(reactors = reaction.reactors :+ userId)
      )
    ))
  }

  override def removeReaction(
    userId: UUID, shortCode: String,
  ): TextMessage = {
    this.reactions.find(_.shortCode == shortCode).fold(this)(reaction => this.copy(
      reactions = reactions.updated(
        reactions.indexOf(reaction),
        reaction.copy(reactors = reaction.reactors.filterNot(_ == userId))
      )
    ))
  }

}

object TextMessage {
  def apply(
    message: String,
    author: Participant,
    reactions: List[Reaction],
    references: List[MessageReference],
    isSelf: Boolean
  ): TextMessage = new TextMessage(
    Message.generateId,
    message,
    author,
    reactions,
    references,
    isSelf
  )
}

case class TextMessageWithLinks(
  messageId: String,
  message: String,
  author: Participant,
  reactions: List[Reaction],
  references: List[MessageReference],
  isSelf: Boolean,
  isOnlyLink: Boolean,
  links: List[MessageLink]
) extends Message
  with Editable
  with Authorable
  with Reactable {

  override def setSelf(
    isSelf: Boolean,
  ): Message with Authorable = this.copy(
    isSelf = isSelf
  )

  override def setReferences(
    references: List[MessageReference],
  ): Message with Editable = this.copy(
    references = references
  )

  override def addReaction(
    userId: UUID, shortCode: String,
  ): TextMessageWithLinks = {
    this.reactions.find(_.shortCode == shortCode).fold(
      this.copy(reactions = reactions :+ Reaction(
        shortCode = shortCode,
        reactors = Seq(userId)
      ))
    )(reaction => this.copy(
      reactions = reactions.updated(
        reactions.indexOf(reaction),
        reaction.copy(reactors = reaction.reactors :+ userId)
      )
    ))
  }

  override def removeReaction(
    userId: UUID, shortCode: String,
  ): TextMessageWithLinks = {
    this.reactions.find(_.shortCode == shortCode).fold(this)(reaction => this.copy(
      reactions = reactions.updated(
        reactions.indexOf(reaction),
        reaction.copy(reactors = reaction.reactors.filterNot(_ == userId))
      )
    ))
  }

}

object TextMessageWithLinks {
  def apply(
    message: String,
    author: Participant,
    reactions: List[Reaction],
    references: List[MessageReference],
    isSelf: Boolean,
    isOnlyLink: Boolean,
    links: List[MessageLink]
  ): TextMessageWithLinks = new TextMessageWithLinks(
    Message.generateId,
    message,
    author,
    reactions,
    references,
    isSelf,
    isOnlyLink,
    links
  )
}

case class TenorMessage(
  messageId: String,
  author: Participant,
  reactions: List[Reaction],
  isSelf: Boolean,
  url: String,
) extends Message
  with Authorable
  with Reactable {

  override def setSelf(
    isSelf: Boolean,
  ): Message with Authorable = this.copy(
    isSelf = isSelf
  )

  override def addReaction(
    userId: UUID, shortCode: String,
  ): TenorMessage = {
    this.reactions.find(_.shortCode == shortCode).fold(
      this.copy(reactions = reactions :+ Reaction(
        shortCode = shortCode,
        reactors = Seq(userId)
      ))
    )(reaction => this.copy(
      reactions = reactions.updated(
        reactions.indexOf(reaction),
        reaction.copy(reactors = reaction.reactors :+ userId)
      )
    ))
  }

  override def removeReaction(
    userId: UUID, shortCode: String,
  ): TenorMessage = {
    this.reactions.find(_.shortCode == shortCode).fold(this)(reaction => this.copy(
      reactions = reactions.updated(
        reactions.indexOf(reaction),
        reaction.copy(reactors = reaction.reactors.filterNot(_ == userId))
      )
    ))
  }

}

object TenorMessage {
  def apply(
    author: Participant,
    reactions: List[Reaction],
    isSelf: Boolean,
    url: String,
  ): TenorMessage = new TenorMessage(
    Message.generateId,
    author,
    reactions,
    isSelf,
    url,
  )
}