package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Provider, ProviderType}
import com.couchmate.common.util.slick.WithTableQuery

class ProviderTable(tag: Tag) extends Table[Provider](tag, "provider") {
  def providerId: Rep[Long] = column[Long]("provider_id", O.PrimaryKey, O.AutoInc)
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id")
  def extId: Rep[String] = column[String]("ext_id")
  def name: Rep[String] = column[String]("name")
  def `type`: Rep[ProviderType] = column[ProviderType]("type")
  def location: Rep[Option[String]] = column[Option[String]]("location")
  def * = (
    providerId.?,
    providerOwnerId,
    extId,
    name,
    `type`,
    location,
  ) <> ((Provider.apply _).tupled, Provider.unapply)

  def sourceFK = foreignKey(
    "provider_owner_fk",
    providerOwnerId,
    ProviderOwnerTable.table
    )(
    _.providerOwnerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def old_sourceIdx = index(
    "provider_owner_idx",
    (providerOwnerId, extId)
  )

  def sourceIdx = index(
    "provider_owner_idx",
    (providerOwnerId, extId),
    unique = true
  )
}

object ProviderTable extends WithTableQuery[ProviderTable] {
  private[couchmate] val table = TableQuery[ProviderTable]

  private[couchmate] val insertOrGetProviderIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_provider_id(
              _provider_owner_id BIGINT,
              _ext_id VARCHAR,
              _name VARCHAR,
              _type VARCHAR,
              _location VARCHAR,
              OUT _provider_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  provider_id
                  FROM    provider
                  WHERE   provider_owner_id = _provider_owner_id AND
                          ext_id = _ext_id
                  FOR     SHARE
                  INTO    _provider_id;

                  EXIT WHEN FOUND;

                  INSERT INTO provider
                  (provider_owner_id, ext_id, name, type, location)
                  VALUES
                  (_provider_owner_id, _ext_id, _name, _type, _location)
                  ON CONFLICT (provider_owner_id, ext_id) DO NOTHING
                  RETURNING provider_id
                  INTO _provider_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
