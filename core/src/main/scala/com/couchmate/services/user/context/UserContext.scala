package com.couchmate.services.user.context

import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.data.{User, UserMeta}
import com.couchmate.common.models.api.user.{UserMute, User => ExternalUser}

case class UserContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String,
  mutes: Seq[UserMute],
  wordMutes: Seq[String],
) {
  def getClientUser: ExternalUser = ExternalUser(
    user.userId.get,
    user.created,
    user.verified,
    user.role,
    userMeta.username,
    userMeta.email,
    token,
    mutes,
    wordMutes
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