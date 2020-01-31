package com.couchmate.db

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.{RoomActivity, RoomActivityType}
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class RoomActivityTable(tag: Tag) extends Table[RoomActivity](tag, "room_activity") {
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[RoomActivityType] = column[RoomActivityType]("action")
  def created: Rep[LocalDateTime] = column[LocalDateTime]("created", O.SqlType("timestamp"))
  def * = (
    airingId,
    userId,
    action,
    created,
  ) <> ((RoomActivity.apply _).tupled, RoomActivity.unapply)

  def airingFk = foreignKey(
    "room_airing_fk",
    airingId,
    AiringTable.table,
    )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def userFk = foreignKey(
    "room_user_fk",
    userId,
    UserTable.table,
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

object RoomActivityTable extends Slickable[RoomActivityTable] {
  val table = TableQuery[RoomActivityTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
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

//  private[this] val getLatestForUsers: Query[(Rep[UUID], Rep[Option[LocalDateTime]]), (UUID, Option[LocalDateTime]), Seq] =
//    roomActivityTable
//      .groupBy(_.userId)
//      .map { case (userId, query) =>
//        userId -> query.map(_.created).max
//      }
//
//  private[this] lazy val getRoomCountCompiled = Compiled { (airingId: Rep[UUID]) =>
//    (for {
//      counts <- getLatestForUsers
//      ra <- roomActivityTable
//      if  ra.airingId === airingId &&
//          ra.action === (RoomActivityType.Joined: RoomActivityType) &&
//          ra.userId === counts._1 &&
//          ra.created === counts._2
//    } yield ra).length
//  }
//
//  def getRoomCount(airingId: UUID): AppliedCompiledFunction[UUID, Rep[Int], Int] = {
//    getRoomCountCompiled(airingId)
//  }
//
//  private[this] lazy val getTotalCountCompiled = Compiled {
//    (for {
//      ra <- roomActivityTable
//      if ra.action === (RoomActivityType.Joined: RoomActivityType)
//      counts <- getLatestForUsers
//      if  ra.userId === counts._1 &&
//        ra.created === counts._2
//    } yield ra).length
//  }
//
//  def getTotalCount: CompiledExecutable[Rep[Int], Int] = {
//    getTotalCountCompiled
//  }
//
//  def addRoomActivity(a: RoomActivityTable): SqlStreamingAction[Vector[RoomActivityTable], RoomActivityTable, Effect] = {
//    sql"""
//         INSERT INTO room_activity
//         (airing_id, user_id, action, created)
//         VALUES
//         (${a.airingId}, ${a.userId}, ${a.action}, ${a.created})
//         RETURNING *
//       """.as[RoomActivityTable]
//  }
}
