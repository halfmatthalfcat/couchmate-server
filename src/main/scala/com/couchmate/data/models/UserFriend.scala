package com.couchmate.data.models

import java.util.UUID

case class UserFriend(
  userId: UUID,
  friendId: UUID
)
