package com.couchmate.data.schema

import com.couchmate.data.models.Channel
import PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class ChannelDAO(tag: Tag) extends Table[Channel](tag, "channel") {
  def channelId: Rep[Long] = column[Long]("channel_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extId: Rep[Long] = column[Long]("ext_id")
  def callsign: Rep[String] = column[String]("callsign")
  def * = (
    channelId.?,
    sourceId,
    extId,
    callsign,
  ) <> ((Channel.apply _).tupled, Channel.unapply)

  def sourceFk = foreignKey(
    "channel_source_fk",
    sourceId,
    SourceDAO.sourceTable,
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def channelSourceIdx = index(
    "channel_source_idx",
    (sourceId, extId)
  )
}

object ChannelDAO {
  val channelTable = TableQuery[ChannelDAO]

  val init = TableMigration(channelTable)
    .create
    .addColumns(
      _.channelId,
      _.sourceId,
      _.extId,
      _.callsign
    ).addForeignKeys(
      _.sourceFk,
    ).addIndexes(
      _.channelSourceIdx,
    )

  def getChannel(channelId: Long)(
    implicit
    db: Database,
  ): Future[Option[Channel]] = {
    db.run(channelTable.filter(_.channelId === channelId).result.headOption)
  }

  def getChannelForSourceAndExt(sourceId: Long, extId: Long)(
    implicit
    db: Database,
  ): Future[Option[Channel]] = {
    db.run(channelTable.filter { channel =>
      channel.sourceId === sourceId &&
      channel.extId === channel.extId
    }.result.headOption)
  }

  def upsertChannel(channel: Channel)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Channel] = {
    channel match {
      case Channel(None, _, _, _) =>
        db.run((channelTable returning channelTable) += channel)
      case Channel(Some(channelId), _, _, _) => for {
        _ <- db.run(channelTable.filter(_.channelId === channelId).update(channel))
        c <- db.run(channelTable.filter(_.channelId === channelId).result.head)
      } yield c
    }
  }
}
