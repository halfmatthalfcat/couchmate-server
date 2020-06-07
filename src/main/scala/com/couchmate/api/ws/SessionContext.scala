package com.couchmate.api.ws

import java.util.UUID

import com.couchmate.data.models.UserRole

case class SessionContext(
  userId: UUID,
  role: UserRole,
  providerId: Long,
  providerName: String,
  token: String
)
