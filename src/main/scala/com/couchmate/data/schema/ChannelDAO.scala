package com.couchmate.data.schema

import com.couchmate.data.models.Channel
import PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

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

  def getChannel(channelId: Long)(
    implicit
    db: Database,
  ): Future[Option[Channel]] = {
    db.run(channelTable.filter(_.channelId === channelId).result.headOption)
  }

  def getChannelForExt(extId: Long)(
    implicit
    db: Database,
  ): Future[Option[Channel]] = {
    db.run(channelTable.filter { channel =>
      channel.extId === channel.extId
    }.result.headOption)
  }

  def upsertChannel(channel: Channel)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Channel] = {
    channel match {
      case Channel(None, _, _) =>
        db.run((channelTable returning channelTable) += channel)
      case Channel(Some(channelId), _, _) => for {
        _ <- db.run(channelTable.filter(_.channelId === channelId).update(channel))
        c <- db.run(channelTable.filter(_.channelId === channelId).result.head)
      } yield c
    }
  }
}
