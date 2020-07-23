package com.couchmate.api.ws.protocol

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.api.Provider
import com.couchmate.common.models.api.user.User
import com.couchmate.common.models.data.UserMeta
import com.couchmate.common.util.json.CountryCodePlayJson
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
case class RestoreSession(
  token: String,
  roomId: Option[UUID],
  timezone: String,
  locale: String,
  region: String
) extends Protocol
case class SetSession(
  user: User,
  provider: String,
  token: String
) extends Protocol

case class Login(
  email: String,
  password: String
) extends Protocol
case class LoginFailure(
  cause: LoginErrorCause
) extends Protocol

case object Logout extends Protocol

case class ForgotPassword(
  email: String
) extends Protocol
case class ForgotPasswordResponse(
  success: Boolean
) extends Protocol

case class ForgotPasswordReset(
  password: String,
  token: String
) extends Protocol
case object ForgotPasswordResetSuccess extends Protocol
case class ForgotPasswordResetFailed(
  cause: ForgotPasswordErrorCause
) extends Protocol

case class ResetPassword(
  currentPassword: String,
  newPassword: String
) extends Protocol
case object ResetPasswordSuccess extends Protocol
case class ResetPasswordFailed(
  cause: PasswordResetErrorCause
) extends Protocol

case class ValidateEmail(
  email: String
) extends Protocol
case class ValidateEmailResponse(
  exists: Boolean,
  valid: Boolean
) extends Protocol

case class ValidateUsername(
  username: String
) extends Protocol
case class ValidateUsernameResponse(
  exists: Boolean,
  valid: Boolean
) extends Protocol

case class RegisterAccount(
  email: String,
  password: String,
) extends Protocol
case class RegisterAccountSuccess(
  user: User
) extends Protocol
case class RegisterAccountFailure(
  cause: RegisterAccountErrorCause
) extends Protocol

case class VerifyAccount(
  token: String,
) extends Protocol
case class VerifyAccountSuccess(
  verified: Boolean
) extends Protocol

case class GetProviders(
  zipCode: String,
  country: CountryCode
) extends Protocol
case class GetProvidersResponse(
  providers: Seq[Provider]
) extends Protocol

case class UpdateProvider(
  zipCode: String,
  providerId: Long
) extends Protocol
case class UpdateProviderResponse(
  success: Boolean,
  pulling: Boolean
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
case class SystemMessage(
  messageId: String,
  message: String
) extends Protocol
case class DirectMessage(
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