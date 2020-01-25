package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Source
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

class SourceDAO(tag: Tag) extends Table[Source](tag, "source") {
  def sourceId: Rep[Long] = column[Long]("source_id", O.PrimaryKey, O.AutoInc)
  def name: Rep[String] = column[String]("name")
  def * = (sourceId.?, name) <> ((Source.apply _).tupled, Source.unapply)
}

object SourceDAO {
  val sourceTable = TableQuery[SourceDAO]

  val init = TableMigration(sourceTable)
    .create
    .addColumns(
      _.sourceId,
      _.name,
    )

  def addSource()(
    implicit
    session: SlickSession,
  ): Flow[Source, Source, NotUsed] = Slick.flowWithPassThrough { source =>
    (sourceTable returning sourceTable) += source
  }
}
