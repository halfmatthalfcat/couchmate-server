package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.SportOrganization
import com.couchmate.common.util.slick.WithTableQuery

class SportOrganizationTable(tag: Tag) extends Table[SportOrganization](tag, "sport_organization") {
  def sportOrganizationId: Rep[Long] = column[Long]("sport_organization_id", O.PrimaryKey, O.AutoInc)
  def extSportId: Rep[Long] = column[Long]("ext_sport_id")
  def extOrgId: Rep[Long] = column[Long]("ext_org_id")
  def sportName: Rep[String] = column[String]("sport_name")
  def orgName: Rep[Option[String]] = column[Option[String]]("org_name")
  def * = (
    sportOrganizationId.?,
    extSportId,
    extOrgId.?,
    sportName,
    orgName,
  ) <> ((SportOrganization.apply _).tupled, SportOrganization.unapply)

  def sourceExtSportOrgIdx = index(
    "source_ext_sport_org_idx",
    (extSportId, extOrgId),
    unique = true
  )
}

object SportOrganizationTable extends WithTableQuery[SportOrganizationTable] {
  private[couchmate] val table = TableQuery[SportOrganizationTable]

  private[couchmate] val insertOrGetSportOrganizationIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_sport_organization_id(
              _ext_sport_id BIGINT,
              _ext_org_id BIGINT,
              _sport_name VARCHAR,
              _org_name VARCHAR,
              OUT _sport_organization_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  sport_organization_id
                  FROM    sport_organization
                  WHERE   ext_sport_id = _ext_sport_id AND
                          ext_org_id = _ext_org_id
                  FOR     SHARE
                  INTO    _sport_organization_id;

                  EXIT WHEN FOUND;

                  INSERT INTO sport_organization
                  (ext_sport_id, ext_org_id, sport_name, org_name)
                  VALUES
                  (_ext_sport_id, _ext_org_id, _sport_name, _org_name)
                  ON CONFLICT (ext_sport_id, ext_org_id) DO NOTHING
                  RETURNING sport_organization_id
                  INTO _sport_organization_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
