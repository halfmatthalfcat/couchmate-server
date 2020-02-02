package com.couchmate.db

import com.couchmate.common.models.SportEvent
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class SportEventTable(tag: Tag) extends Table[SportEvent](tag, "sport_event") {
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

object SportEventTable extends Slickable[SportEventTable] {
  val table = TableQuery[SportEventTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
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
}
