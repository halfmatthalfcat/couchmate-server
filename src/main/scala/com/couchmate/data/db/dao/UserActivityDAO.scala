package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserActivityTable
import com.couchmate.data.models.UserActivity

import scala.concurrent.{ExecutionContext, Future}

class UserActivityDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def addUserActivity(userActivity: UserActivity): Future[UserActivity] = {
    db.run((UserActivityTable.table returning UserActivityTable.table) += userActivity)
  }

}
