package com.couchmate.db.dao

import com.couchmate.common.models.UserActivity
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.UserActivityQueries
import com.couchmate.db.table.UserActivityTable

import scala.concurrent.{ExecutionContext, Future}

class UserActivityDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends UserActivityQueries {

  def addUserActivity(userActivity: UserActivity): Future[UserActivity] = {
    db.run((UserActivityTable.table returning UserActivityTable.table) += userActivity)
  }

}
