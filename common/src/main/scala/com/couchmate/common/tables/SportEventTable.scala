package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.SportEvent
import com.couchmate.common.util.slick.WithTableQuery

class SportEventTable(tag: Tag) extends Table[SportEvent](tag, "sport_event") {
  def sportEventId: Rep[Long] = column[Long]("sport_event_id", O.PrimaryKey, O.AutoInc)
  def sportOrganizationId: Rep[Long] = column[Long]("sport_organization_id")
  def sportEventTitle: Rep[String] = column[String]("sport_event_title")
  def * = (
    sportEventId.?,
    sportOrganizationId.?,
    sportEventTitle,
  ) <> ((SportEvent.apply _).tupled, SportEvent.unapply)

  def sportOrganizationFk = foreignKey(
    "sport_event_sport_org_fk",
    sportOrganizationId,
    SportOrganizationTable.table,
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

object SportEventTable extends WithTableQuery[SportEventTable] {
  private[couchmate] val table = TableQuery[SportEventTable]

  private[couchmate] val insertOrGetSportEventIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_sport_event_id(
              _sport_organization_id BIGINT,
              _sport_event_title VARCHAR,
              OUT _sport_event_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  sport_event_id
                  FROM    sport_event
                  WHERE   sport_organization_id = _sport_organization_id AND
                          sport_event_title = _sport_event_title
                  FOR     SHARE
                  INTO    _sport_event_id;

                  EXIT WHEN FOUND;

                  INSERT INTO sport_event
                  (sport_organization_id, sport_event_title)
                  VALUES
                  (_sport_organization_id, _sport_event_title)
                  ON CONFLICT (sport_organization_id, sport_event_title) DO NOTHING
                  RETURNING sport_event_id
                  INTO _sport_event_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
