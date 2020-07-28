package com.couchmate.api.ws

import java.util.UUID

import com.couchmate.common.models.api.grid.{Grid, GridAiring}
import com.couchmate.common.models.data.{RoomStatusType, User, UserMeta}
import com.couchmate.common.models.api.user.{ User => ExternalUser }

case class SessionContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String,
  mutes: Seq[UUID],
  airings: Set[GridAiring],
  grid: Grid,
) {
  def setAiringsFromGrid(grid: Grid): SessionContext =
    this.copy(
      airings = grid.pages.foldLeft(Set.empty[GridAiring]) {
        case (a1, gridPage) => a1 ++ gridPage.channels.foldLeft(Set.empty[GridAiring]) {
          case (a2, channel) => a2 ++ channel.airings
        }
      }
    )

  def roomIsOpen(airingId: UUID): Boolean =
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
  )
}
