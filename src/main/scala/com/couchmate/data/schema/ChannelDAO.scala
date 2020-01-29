package com.couchmate.data.schema

import com.couchmate.data.models.Channel
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.{AppliedCompiledFunction, Tag}
import slick.migration.api.TableMigration
import slick.sql.SqlStreamingAction

class ChannelDAO(tag: Tag) extends Table[Channel](tag, "channel") {
  def channelId: Rep[Long] = column[Long]("channel_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def callsign: Rep[String] = column[String]("callsign")
  def * = (
    channelId.?,
    extId,
    callsign,
  ) <> ((Channel.apply _).tupled, Channel.unapply)
}

object ChannelDAO {
  val channelTable = TableQuery[ChannelDAO]

  val init = TableMigration(channelTable)
    .create
    .addColumns(
      _.channelId,
      _.extId,
      _.callsign
    )

  private[this] lazy val getChannelCompiled = Compiled { (channelId: Rep[Long]) =>
    channelTable.filter(_.channelId === channelId)
  }

  def getChannel(channelId: Long): AppliedCompiledFunction[Long, Query[ChannelDAO, Channel, Seq], Seq[Channel]] = {
    getChannelCompiled(channelId)
  }

  private[this] lazy val getChannelForExtCompiled = Compiled { (extId: Rep[Long]) =>
    channelTable.filter(_.extId === extId)
  }

  def getChannelForExt(extId: Long): AppliedCompiledFunction[Long, Query[ChannelDAO, Channel, Seq], Seq[Channel]] = {
    getChannelForExtCompiled(extId)
  }

  def upsertChannel(c: Channel): SqlStreamingAction[Vector[Channel], Channel, Effect] = {
    sql"""
         INSERT INTO channel
         (channel_id, source_id, ext_id, callsign)
         VALUES
         (${c.channelId}, ${c.extId}, ${c.callsign})
         ON CONFLICT (channel_id)
         DO UPDATE SET
            ext_id =    ${c.extId},
            callsign =  ${c.callsign}
         RETURNING *
       """.as[Channel]
  }
}
