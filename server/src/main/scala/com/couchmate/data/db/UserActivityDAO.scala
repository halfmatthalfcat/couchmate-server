package com.couchmate.data.db

import com.couchmate.common.models.UserActivity

class UserActivityDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  def addUserActivity(userActivity: UserActivity) = quote {
    query[UserActivity]
      .insert(lift(userActivity))
      .returning(ua => ua)
  }

}
