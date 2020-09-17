package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserActivity
import com.couchmate.common.tables.UserActivityTable
import slick.sql.SqlStreamingAction

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

  private[common] def getProviderUserCount(providerId: Long): SqlStreamingAction[Seq[Long], Long, Effect] =
    sql"""
          SELECT count(*)
          FROM (
            SELECT current.user_id
            FROM user_activity current
            JOIN (
              SELECT    user_id, max(created) as created
              FROM      user_activity
              GROUP BY  user_id
            ) as latest
            ON    current.user_id = latest.user_id AND
                  current.created = latest.created
            JOIN  user_provider up
            ON    current.user_id = up.user_id
            WHERE current.action = 'login' AND
                  up.provider_id = $providerId
          ) as c
         """.as[Long]

}