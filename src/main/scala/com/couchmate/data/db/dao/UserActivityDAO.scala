package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserActivityTable
import com.couchmate.data.models.UserActivity

import scala.concurrent.{ExecutionContext, Future}

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

  private[dao] def addUserActivity(userActivity: UserActivity): DBIO[UserActivity] =
    (UserActivityTable.table returning UserActivityTable.table) += userActivity

}