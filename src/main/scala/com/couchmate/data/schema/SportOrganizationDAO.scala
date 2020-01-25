package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.SportOrganization
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class SportOrganizationDAO(tag: Tag) extends Table[SportOrganization](tag, "sport_organization") {
  def sportOrganizationId: Rep[Long] = column[Long]("sport_organization_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extSportId: Rep[Long] = column[Long]("ext_sport_id")
  def extOrgId: Rep[Int] = column[Int]("ext_org_id", O.Default(-1))
  def sportName: Rep[String] = column[String]("sport_name")
  def orgName: Rep[String] = column[String]("org_name")
  def * = (
    sportOrganizationId.?,
    sourceId,
    extSportId,
    extOrgId.?,
    sportName,
    orgName.?,
  ) <> ((SportOrganization.apply _).tupled, SportOrganization.unapply)

  def sourceFk = foreignKey(
    "sport_org_source_fk",
    sourceId,
    SourceDAO.sourceTable,
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def sourceExtSportOrgIdx = index(
    "source_ext_sport_org_idx",
    (sourceId, extSportId, extOrgId),
    unique = true
  )
}

object SportOrganizationDAO {
  val sportOrganizationTable = TableQuery[SportOrganizationDAO]

  val init = TableMigration(sportOrganizationTable)
    .create
    .addColumns(
      _.sportOrganizationId,
      _.sourceId,
      _.extSportId,
      _.extOrgId,
      _.sportName,
      _.orgName,
    ).addForeignKeys(
      _.sourceFk,
    ).addIndexes(
      _.sourceExtSportOrgIdx,
    )

  def getSportOrganization()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[SportOrganization], NotUsed] = Slick.flowWithPassThrough { sportOrganizationId =>
    sportOrganizationTable.filter(_.sportOrganizationId === sportOrganizationId).result.headOption
  }

  def getSportOrganizationBySourceSportAndOrg()(
    implicit
    session: SlickSession,
  ): Flow[(Long, Long, Option[Int]), Option[SportOrganization], NotUsed] = Slick.flowWithPassThrough {
    case (sourceId, extSportId, extOrgId) => sportOrganizationTable.filter { sportOrg =>
      sportOrg.sourceId === sourceId &&
      sportOrg.extSportId === extSportId &&
      sportOrg.extOrgId === extOrgId
    }.result.headOption
  }

  def upsertSportOrganization()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[SportOrganization, SportOrganization, NotUsed] = Slick.flowWithPassThrough {
    case sportOrganization @ SportOrganization(None, _, _, _, _, _) =>
      (sportOrganizationTable returning sportOrganizationTable) += sportOrganization
    case sportOrganization @ SportOrganization(Some(sportOrganizationId), _, _, _, _, _) => for {
      _ <- sportOrganizationTable.filter(_.sportOrganizationId === sportOrganizationId).update(sportOrganization)
      so <- sportOrganizationTable.filter(_.sportOrganizationId === sportOrganizationId).result.head
    } yield so
  }
}
