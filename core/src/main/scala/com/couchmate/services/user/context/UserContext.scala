package com.couchmate.services.user.context

import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.data.{User, UserChannelFavorite, UserMeta, UserNotificationConfiguration, UserNotifications}
import com.couchmate.common.models.api.user.{UserMute, User => ExternalUser}

case class UserContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String,
  mutes: Seq[UserMute],
  wordMutes: Seq[String],
  notifications: UserNotifications,
  notificationConfigurations: Seq[UserNotificationConfiguration],
  favoriteChannels: Seq[UserChannelFavorite]
) {
  def getClientUser(
    deviceId: Option[String]
  ): ExternalUser = ExternalUser(
    user.userId.get,
    user.created,
    user.verified,
    user.role,
    userMeta.username,
    userMeta.email,
    token,
    mutes,
    wordMutes,
    notificationConfigurations
      .find(_.deviceId == deviceId)
      .exists(_.active),
    notifications,
    favoriteChannels.map(_.providerChannelId)
  )
}

object UserContext {
  implicit def toParticipant(userContext: UserContext): Participant =
    Participant(
      userContext.user.userId.get,
      userContext.userMeta.username,
      List.empty
    )
}