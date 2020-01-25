package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.SportEvent
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class SportEventDAO(tag: Tag) extends Table[SportEvent](tag, "sport_event") {
  def sportEventId: Rep[Long] = column[Long]("sport_event_id", O.PrimaryKey, O.AutoInc)
  def sportOrganizationId: Rep[Long] = column[Long]("sport_organization_id")
  def sportEventTitle: Rep[String] = column[String]("sport_event_title")
  def * = (
    sportEventId.?,
    sportOrganizationId,
    sportEventTitle,
  ) <> ((SportEvent.apply _).tupled, SportEvent.unapply)

  def sportOrganizationFk = foreignKey(
    "sport_event_sport_org_fk",
    sportOrganizationId,
    SportOrganizationDAO.sportOrganizationTable,
  )(
    _.sportOrganizationId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def sportEventUniqueIdx = index(
    "sport_event_name_unique_idx",
    (sportOrganizationId, sportEventTitle),
    unique = true
  )
}

object SportEventDAO {
  val sportEventTable = TableQuery[SportEventDAO]

  val init = TableMigration(sportEventTable)
    .create
    .addColumns(
      _.sportEventId,
      _.sportOrganizationId,
      _.sportEventTitle,
    ).addForeignKeys(
      _.sportOrganizationFk,
    ).addIndexes(
      _.sportEventUniqueIdx,
    )

  def getSportEvent()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[SportEvent], NotUsed] = Slick.flowWithPassThrough { sportEventId =>
    sportEventTable.filter(_.sportEventId === sportEventId).result.headOption
  }

  def upsertSportEvent()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[SportEvent, SportEvent, NotUsed] = Slick.flowWithPassThrough {
    case sportEvent @ SportEvent(None, _, _) =>
      (sportEventTable returning sportEventTable) += sportEvent
    case sportEvent @ SportEvent(Some(sportEventId), _, _) => for {
      _ <- sportEventTable.filter(_.sportEventId === sportEventId).update(sportEvent)
      se <- sportEventTable.filter(_.sportEventId === sportEventId).result.head
    } yield se
  }
}
