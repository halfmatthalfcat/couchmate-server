package com.couchmate.db

import com.couchmate.common.models.Channel
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ChannelTable(tag: Tag) extends Table[Channel](tag, "channel") {
  def channelId: Rep[Long] = column[Long]("channel_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def callsign: Rep[String] = column[String]("callsign")
  def * = (
    channelId.?,
    extId,
    callsign,
  ) <> ((Channel.apply _).tupled, Channel.unapply)
}

object ChannelTable extends Slickable[ChannelTable] {
  val table = TableQuery[ChannelTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.channelId,
      _.extId,
      _.callsign
    )

//  private[this] lazy val getChannelCompiled = Compiled { (channelId: Rep[Long]) =>
//    channelTable.filter(_.channelId === channelId)
//  }
//
//  def getChannel(channelId: Long): AppliedCompiledFunction[Long, Query[ChannelTable, ChannelTable, Seq], Seq[ChannelTable]] = {
//    getChannelCompiled(channelId)
//  }
//
//  private[this] lazy val getChannelForExtCompiled = Compiled { (extId: Rep[Long]) =>
//    channelTable.filter(_.extId === extId)
//  }
//
//  def getChannelForExt(extId: Long): AppliedCompiledFunction[Long, Query[ChannelTable, ChannelTable, Seq], Seq[ChannelTable]] = {
//    getChannelForExtCompiled(extId)
//  }
//
//  def upsertChannel(c: ChannelTable): SqlStreamingAction[Vector[ChannelTable], ChannelTable, Effect] = {
//    sql"""
//         INSERT INTO channel
//         (channel_id, source_id, ext_id, callsign)
//         VALUES
//         (${c.channelId}, ${c.extId}, ${c.callsign})
//         ON CONFLICT (channel_id)
//         DO UPDATE SET
//            ext_id =    ${c.extId},
//            callsign =  ${c.callsign}
//         RETURNING *
//       """.as[ChannelTable]
//  }
}
