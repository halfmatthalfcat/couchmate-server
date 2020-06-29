package com.couchmate.common.models.data

import java.util.UUID

case class UserFriend(
  userId: UUID,
  friendId: UUID
)
