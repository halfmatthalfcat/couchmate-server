package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserActivity
import com.couchmate.common.tables.UserActivityTable

import scala.concurrent.Future

trait UserActivityDAO {

  def addUserActivity(userActivity: UserActivity)(
    implicit
    db: Database
  ): Future[UserActivity] =
    db.run(UserActivityDAO.addUserActivity(userActivity))

  def addUserActivity$()(
    implicit
    session: SlickSession
  ): Flow[UserActivity, UserActivity, NotUsed] =
    Slick.flowWithPassThrough(UserActivityDAO.addUserActivity)

}

object UserActivityDAO {

  private[common] def addUserActivity(userActivity: UserActivity): DBIO[UserActivity] =
    (UserActivityTable.table returning UserActivityTable.table) += userActivity

}