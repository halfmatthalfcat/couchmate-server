package com.couchmate.api.ws.protocol

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import com.couchmate.api.models.grid.Grid
import com.couchmate.api.models.room.Participant
import com.couchmate.api.models.{Provider, User}
import com.couchmate.data.models.{UserMeta, UserRole}
import com.couchmate.util.json.CountryCodePlayJson
import com.neovisionaries.i18n.CountryCode
import julienrf.json.derived
import play.api.libs.json.{Format, __}

sealed trait Protocol

object Protocol extends CountryCodePlayJson {
  implicit val format: Format[Protocol] =
    derived.flat.oformat((__ \ "ttype").format[String])
}

case class InitSession(
  timezone: String,
  locale: String,
  region: String
) extends Protocol
case class Login(
  email: String,
  password: String
) extends Protocol
case class RestoreSession(
  token: String,
  roomId: Option[UUID]
) extends Protocol
case class SetSession(
  user: User,
  provider: String,
  token: String
) extends Protocol

case class ValidateNewAccount(
  email: String,
  username: String,
) extends Protocol
case class ValidateNewAccountResponse(
  status: ValidateAccountStatus
) extends Protocol

case class RegisterAccount(
  email: String,
  username: String,
) extends Protocol
case class RegisterAccountResponse(
  meta: UserMeta
) extends Protocol
case class RegisterAccountFailure(
  cause: RegisterAccountError
) extends Protocol

case class GetProviders(
  zipCode: String,
  country: CountryCode
) extends Protocol
case class GetProvidersResponse(
  providers: Seq[Provider]
) extends Protocol

case class UpdateGrid(
  grid: Grid
) extends Protocol

case class JoinRoom(
  airingId: UUID
) extends Protocol
case object LeaveRoom extends Protocol
case object RoomEnded extends Protocol
case class RoomJoined(
  airingId: UUID
) extends Protocol
case class SetParticipants(
  participants: Seq[Participant]
) extends Protocol
case class AddParticipant(
  participant: Participant
) extends Protocol
case class RemoveParticipant(
  participant: Participant
) extends Protocol
case class MuteParticipant(
  participant: Participant
) extends Protocol

case class SendMessage(
  message: String
) extends Protocol

case class RoomMessage(
  messageId: String,
  participant: Participant,
  isSelf: Boolean,
  message: String,
) extends Protocol

object RoomMessage {
  def apply(
    participant: Participant,
    isSelf: Boolean,
    message: String,
  ): RoomMessage = {
    val instant: Instant = Instant.now()
    val seconds: Long = instant.getEpochSecond
    val nano: Long = instant.truncatedTo(ChronoUnit.MICROS).getNano

    new RoomMessage(
      s"$seconds.$nano",
      participant,
      isSelf,
      message
    )
  }
}

case class LockSending(
  duration: Int
) extends Protocol
case object UnlockSending extends Protocol