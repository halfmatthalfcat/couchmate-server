package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.SportTeam
import com.couchmate.common.util.slick.WithTableQuery

class SportTeamTable(tag: Tag) extends Table[SportTeam](tag, "sport_team") {
  def sportTeamId: Rep[Long] = column[Long]("sport_team_id", O.PrimaryKey, O.AutoInc)
  def extSportTeamId: Rep[Long] = column[Long]("ext_sport_team_id", O.Unique)
  def name: Rep[String] = column[String]("name")

  def * = (
    sportTeamId.?,
    extSportTeamId,
    name
  ) <> ((SportTeam.apply _).tupled, SportTeam.unapply)

  def uniqueExtId = index(
    "ext_sport_team_idx",
    extSportTeamId,
    unique = true
  )
}

object SportTeamTable extends WithTableQuery[SportTeamTable] {
  private[couchmate] val table: TableQuery[SportTeamTable] =
    TableQuery[SportTeamTable]
}