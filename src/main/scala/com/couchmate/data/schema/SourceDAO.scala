package com.couchmate.data.schema

import com.couchmate.data.models.Source
import PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

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

  def addSource(source: Source)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Source] = {
    db.run(
      (sourceTable returning sourceTable) += source
    )
  }
}
