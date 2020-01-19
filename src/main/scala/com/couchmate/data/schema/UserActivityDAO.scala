package com.couchmate.data.schema

import java.time.OffsetDateTime
import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.{UserActivity, UserActivityType}
import slick.lifted.Tag

import scala.concurrent.Future

class UserActivityDAO(tag: Tag)
  extends Table[UserActivity](tag, "user_activity")
  with EnumMappers {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[UserActivityType] = column[UserActivityType]("action")
  def created: Rep[OffsetDateTime] = column[OffsetDateTime]("created")
  def * = (
    userId,
    action,
    created,
  ) <> ((UserActivity.apply _).tupled, UserActivity.unapply)

  def userFk = foreignKey(
    "user_user_activity_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserActivityDAO extends EnumMappers {
  val userActivityTable = TableQuery[UserActivityDAO]

  def addUserActivity(userActivity: UserActivity)(
    implicit
    db: Database,
  ): Future[UserActivity] = {
    db.run((userActivityTable returning userActivityTable) += userActivity)
  }
}
