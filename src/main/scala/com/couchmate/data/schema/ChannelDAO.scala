package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Channel
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

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

  def getChannel()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[Channel], NotUsed] = Slick.flowWithPassThrough { channelId =>
    channelTable.filter(_.channelId === channelId).result.headOption
  }

  def getChannelForSourceAndExt()(
    implicit
    session: SlickSession,
  ): Flow[(Long, Long), Option[Channel], NotUsed] = Slick.flowWithPassThrough {
    case (sourceId, extId) => channelTable.filter { channel =>
      channel.sourceId === sourceId &&
      channel.extId === extId
    }.result.headOption
  }

  def upsertChannel()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[Channel, Channel, NotUsed] = Slick.flowWithPassThrough {
    case channel @ Channel(None, _, _, _) =>
     (channelTable returning channelTable) += channel
    case channel @ Channel(Some(channelId), _, _, _) => for {
      _ <- channelTable.filter(_.channelId === channelId).update(channel)
      c <- channelTable.filter(_.channelId === channelId).result.head
    } yield c
  }
}
