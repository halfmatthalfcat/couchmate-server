package com.couchmate.api.ws

import com.couchmate.data.models.{User, UserMeta}

case class SessionContext(
  user: User,
  userMeta: UserMeta,
  providerId: Long,
  providerName: String,
  token: String
)
