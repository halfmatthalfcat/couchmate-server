package com.couchmate.api.ws

import com.couchmate.common.models.api.grid.{Grid, GridAiring, GridAiringExtended}
import com.couchmate.common.models.data.{RoomStatusType, User, UserMeta}
import com.couchmate.common.models.api.user.{UserMute, User => ExternalUser}

case class SessionContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String,
  mutes: Seq[UserMute],
  wordMutes: Seq[String],
  airings: Set[GridAiringExtended],
  grid: Grid,
) {
  def setAiringsFromGrid(grid: Grid): SessionContext =
    this.copy(
      airings = grid.pages.foldLeft(Set.empty[GridAiringExtended]) {
        case (a1, gridPage) => a1 ++ gridPage.channels.foldLeft(Set.empty[GridAiringExtended]) {
          case (a2, channel) => a2 ++ channel.airings
        }
      }
    )

  def roomIsOpen(airingId: String): Boolean =
    this.airings.exists { airing =>
      airing.airingId == airingId &&
        (
          airing.status == RoomStatusType.PreGame ||
          airing.status == RoomStatusType.Open
        )
    }

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
