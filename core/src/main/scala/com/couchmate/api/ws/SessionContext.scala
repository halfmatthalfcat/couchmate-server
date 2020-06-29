package com.couchmate.api.ws

import java.util.UUID

import com.couchmate.common.models.api.grid.GridAiring
import com.couchmate.common.models.data.{User, UserMeta}

case class SessionContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String,
  muted: Seq[UUID],
  airings: Set[GridAiring]
)
