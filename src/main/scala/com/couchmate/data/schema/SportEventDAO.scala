package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.SportEvent
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

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

  def getSportEvent(sportEventId: Long)(
    implicit
    db: Database,
  ): Future[Option[SportEvent]] = {
    db.run(sportEventTable.filter(_.sportEventId === sportEventId).result.headOption)
  }

  def upsertSportEvent(sportEvent: SportEvent)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[SportEvent] = {
    sportEvent match {
      case SportEvent(None, _, _) =>
        db.run((sportEventTable returning sportEventTable) += sportEvent)
      case SportEvent(Some(sportEventId), _, _) => for {
        _ <- db.run(sportEventTable.filter(_.sportEventId === sportEventId).update(sportEvent))
        se <- db.run(sportEventTable.filter(_.sportEventId === sportEventId).result.head)
      } yield se
    }
  }
}
