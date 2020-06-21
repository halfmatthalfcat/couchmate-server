package com.couchmate.api.ws

import java.util.UUID

import com.couchmate.api.models.grid.{Grid, GridAiring}
import com.couchmate.data.models.{User, UserMeta}

case class SessionContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String,
  muted: Seq[UUID],
  airings: Set[GridAiring]
)
