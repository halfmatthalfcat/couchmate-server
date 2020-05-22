package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserActivityTable
import com.couchmate.data.models.UserActivity

import scala.concurrent.{ExecutionContext, Future}

trait UserActivityDAO {

  def addUserActivity(userActivity: UserActivity)(
    implicit
    db: Database
  ): Future[UserActivity] = {
    db.run((UserActivityTable.table returning UserActivityTable.table) += userActivity)
  }

}
