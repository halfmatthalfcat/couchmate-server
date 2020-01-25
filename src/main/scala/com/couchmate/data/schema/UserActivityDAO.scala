package com.couchmate.data.schema

import java.time.OffsetDateTime
import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.{UserActivity, UserActivityType}
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

class UserActivityDAO(tag: Tag) extends Table[UserActivity](tag, "user_activity") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[UserActivityType] = column[UserActivityType]("action")
  def created: Rep[OffsetDateTime] = column[OffsetDateTime]("created", O.SqlType("timestamptz"))
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

object UserActivityDAO {
  val userActivityTable = TableQuery[UserActivityDAO]

  val init = TableMigration(userActivityTable)
    .create
    .addColumns(
      _.userId,
      _.action,
      _.created,
    ).addForeignKeys(
      _.userFk,
    )

  def addUserActivity()(
    implicit
    session: SlickSession,
  ): Flow[UserActivity, UserActivity, NotUsed] = Slick.flowWithPassThrough { userActivity =>
    (userActivityTable returning userActivityTable) += userActivity
  }
}
