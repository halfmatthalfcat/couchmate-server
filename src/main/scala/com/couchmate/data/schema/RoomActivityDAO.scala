package com.couchmate.data.schema

import java.time.OffsetDateTime
import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.{RoomActivity, RoomActivityType}
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.Future

class RoomActivityDAO(tag: Tag) extends Table[RoomActivity](tag, "room_activity") {

  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[RoomActivityType] = column[RoomActivityType]("action")
  def created: Rep[OffsetDateTime] = column[OffsetDateTime]("created", O.SqlType("timestamptz"))
  def * = (
    airingId,
    userId,
    action,
    created,
  ) <> ((RoomActivity.apply _).tupled, RoomActivity.unapply)

  def airingFk = foreignKey(
    "room_airing_fk",
    airingId,
    AiringDAO.airingTable,
  )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def userFk = foreignKey(
    "room_user_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def roomActivityIdx = index(
    "room_activity_active_idk",
    (airingId, action, created),
  )
}

object RoomActivityDAO {
  val roomActivityTable = TableQuery[RoomActivityDAO]

  val init = TableMigration(roomActivityTable)
    .create
    .addColumns(
      _.airingId,
      _.userId,
      _.action,
      _.created,
    ).addForeignKeys(
      _.airingFk,
      _.userFk,
    ).addIndexes(
      _.roomActivityIdx,
    )

  private[this] val getLatestForUsers =
    roomActivityTable
      .groupBy(_.userId)
      .map { case (userId, query) =>
        userId -> query.map(_.created).max
      }

  def getRoomCount(airingId: UUID)(
    implicit
    db: Database,
  ): Future[Int] = {
    db.run((for {
      ra <- roomActivityTable
      if  ra.airingId === airingId &&
          ra.action === (RoomActivityType.Joined: RoomActivityType)
      counts <- getLatestForUsers
      if  ra.userId === counts._1 &&
          ra.created === counts._2
    } yield ra).length.result)
  }

  def getTotalCount()(
    implicit
    db: Database,
  ): Future[Int] = {
    db.run((for {
      ra <- roomActivityTable
      if ra.action === (RoomActivityType.Joined: RoomActivityType)
      counts <- getLatestForUsers
      if  ra.userId === counts._1 &&
        ra.created === counts._2
    } yield ra).length.result)
  }

  def addRoomActivity(activity: RoomActivity)(
    implicit
    db: Database,
  ): Future[RoomActivity] = {
    db.run((roomActivityTable returning roomActivityTable) += activity)
  }
}
