package com.couchmate.api.ws.protocol

import java.util.UUID

import com.couchmate.common.models.api.Provider
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.api.room.message.Message
import com.couchmate.common.models.api.room.tenor.TenorGif
import com.couchmate.common.models.api.user.{User, UserMute}
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationSeries, UserNotificationShow, UserNotificationTeam, UserReportType}
import com.couchmate.common.util.json.CountryCodePlayJson
import com.neovisionaries.i18n.CountryCode
import julienrf.json.derived
import play.api.libs.json.{Format, __}

sealed trait Protocol

object Protocol extends CountryCodePlayJson {
  implicit val format: Format[Protocol] =
    derived.flat.oformat((__ \ "ttype").format[String])
}

object External {
  case object Ping extends Protocol
  case object Pong extends Protocol
  case object Disconnect extends Protocol

  case class InitSession(
    timezone: String,
    locale: String,
    region: String,
    os: Option[String],
    osVersion: Option[String],
    brand: Option[String],
    model: Option[String]
  ) extends Protocol
  case class RestoreSession(
    token: String,
    roomId: Option[String],
    timezone: String,
    locale: String,
    region: String,
    os: Option[String],
    osVersion: Option[String],
    brand: Option[String],
    model: Option[String]
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

  case class SetNotificationToken(token: String) extends Protocol
  case object EnableNotifications extends Protocol
  case object DisableNotifications extends Protocol

  case object NotificationsEnabled extends Protocol
  case object NotificationsDisabled extends Protocol

  case class AddShowNotification(
    airingId: String,
    channelId: Long,
    hash: Option[String]
  ) extends Protocol
  case class RemoveShowNotification(
    airingId: String,
    channelId: Long
  ) extends Protocol
  case class ToggleShowNotification(
    airingId: String,
    channelId: Long,
    enabled: Boolean,
    hash: Option[String]
  ) extends Protocol
  case class SetNewShowNotification(
    airingId: String,
    channelId: Long,
    enabled: Boolean
  ) extends Protocol
  case class SetHashShowNotification(
    airingId: String,
    channelId: Long,
    hash: String
  ) extends Protocol

  case class AddSeriesNotification(
    seriesId: Long,
    channelId: Long,
    hash: Option[String]
  ) extends Protocol
  case class RemoveSeriesNotification(
    seriesId: Long,
    channelId: Long
  ) extends Protocol
  case class ToggleSeriesNotification(
    seriesId: Long,
    channelId: Long,
    enabled: Boolean,
    hash: Option[String]
  ) extends Protocol
  case class SetNewSeriesNotification(
    seriesId: Long,
    channelId: Long,
    enabled: Boolean
  ) extends Protocol
  case class SetHashSeriesNotification(
    seriesId: Long,
    channelId: Long,
    hash: String
  ) extends Protocol

  case class AddTeamNotification(
    teamId: Long,
    hash: Option[String]
  ) extends Protocol
  case class RemoveTeamNotification(
    teamId: Long,
  ) extends Protocol
  case class ToggleTeamNotification(
    teamId: Long,
    enabled: Boolean,
    hash: Option[String]
  ) extends Protocol
  case class SetNewTeamNotification(
    teamId: Long,
    enabled: Boolean
  ) extends Protocol
  case class SetHashTeamNotification(
    teamId: Long,
    hash: String
  ) extends Protocol

  case class UpdateNotifications(
    show: Seq[UserNotificationShow],
    series: Seq[UserNotificationSeries],
    team: Seq[UserNotificationTeam]
  ) extends Protocol

  case object NotificationAddedSuccess extends Protocol
  case object NotificationAddedFailed extends Protocol

  case object NotificationToggleSuccess extends Protocol
  case object NotificationToggleFailed extends Protocol

  case object NotificationOnlyNewChanged extends Protocol
  case object NotificationOnlyNewChangeFailed extends Protocol

  case class ReadNotification(notificationId: UUID) extends Protocol

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

  case class UpdateUsername(
    username: String
  ) extends Protocol
  case class UpdateUsernameSuccess(
    user: User
  ) extends Protocol
  case class UpdateUsernameFailure(
    cause: UpdateUsernameErrorCause
  ) extends Protocol

  case class RegisterAccount(
    email: String,
    password: String,
  ) extends Protocol
  case object RegisterAccountSentSuccess extends Protocol
  case class RegisterAccountSentFailure(
    cause: RegisterAccountErrorCause
  ) extends Protocol

  case class VerifyAccount(
    token: String,
  ) extends Protocol
  case class VerifyAccountSuccess(
    user: User
  ) extends Protocol
  case object VerifyAccountSuccessWeb extends Protocol
  case class VerifyAccountFailed(
    cause: RegisterAccountErrorCause
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
    airingId: String,
    hash: Option[String]
  ) extends Protocol
  case object RoomClosed extends Protocol
  case object LeaveRoom extends Protocol
  case object RoomEnded extends Protocol
  case class RoomJoined(
    airingId: String,
    hash: String
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

  case class UpdateHashRooms(
    rooms: Map[String, Int]
  ) extends Protocol
  case class ChangeHashRoom(
    name: String
  ) extends Protocol
  case class HashRoomJoined(
    name: String
  ) extends Protocol

  case class MuteParticipant(
    userId: UUID
  ) extends Protocol
  case class UnmuteParticipant(
    userId: UUID
  ) extends Protocol
  case class UpdateMutes(
    mutes: Seq[UserMute]
  ) extends Protocol

  case class ReportParticipant(
    userId: UUID,
    userReportType: UserReportType,
    message: Option[String]
  ) extends Protocol
  case object ReportSuccess extends Protocol
  case object ReportFailed extends Protocol

  case class MuteWord(word: String) extends Protocol
  case class UnmuteWord(word: String) extends Protocol
  case class UpdateWordMutes(words: Seq[String]) extends Protocol

  case class SendMessage(
    message: String
  ) extends Protocol
  case class SendGif(
    url: String
  ) extends Protocol

  case class AppendMessage(
    message: Message
  ) extends Protocol
  case class UpdateMessage(
    message: Message
  ) extends Protocol

  case class AddReaction(
    messageId: String,
    shortCode: String
  ) extends Protocol
  case class RemoveReaction(
    messageId: String,
    shortCode: String
  ) extends Protocol

  case object AddReactionSuccess extends Protocol
  case object RemoveReactionSuccess extends Protocol

  case object GetTenorTrending extends Protocol
  case class TenorTrendingResults(keywords: Seq[String]) extends Protocol

  case class TenorSearch(search: String) extends Protocol
  case class TenorSearchResults(gifs: Seq[TenorGif]) extends Protocol

  case class MessageReplay(
    messages: List[Message]
  ) extends Protocol

  case class LockSending(
    duration: Int
  ) extends Protocol
  case object UnlockSending extends Protocol
}
