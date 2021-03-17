package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.ProviderOwner
import com.couchmate.common.util.slick.WithTableQuery

class ProviderOwnerTable(tag: Tag) extends Table[ProviderOwner](tag, "provider_owner") {
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id", O.PrimaryKey, O.AutoInc)
  def extProviderOwnerId: Rep[String] = column[String]("ext_provider_owner_id", O.Unique)
  def name: Rep[String] = column[String]("name")
  def * = (
    providerOwnerId.?,
    extProviderOwnerId,
    name,
  ) <> ((ProviderOwner.apply _).tupled, ProviderOwner.unapply)

  def idx = index(
    "ext_provider_id_idx",
    extProviderOwnerId,
    unique = true
  )
}

object ProviderOwnerTable extends WithTableQuery[ProviderOwnerTable] {
  private[couchmate] val table = TableQuery[ProviderOwnerTable]

  private[couchmate] val insertOrGetProviderOwnerIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_provider_owner_id(
              _ext_provider_owner_id VARCHAR,
              _name VARCHAR,
              OUT _provider_owner_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  provider_owner_id
                  FROM    provider_owner
                  WHERE   ext_provider_owner_id = _ext_provider_owner_id
                  FOR     SHARE
                  INTO    _provider_owner_id;

                  EXIT WHEN FOUND;

                  INSERT INTO provider_owner
                  (ext_provider_owner_id, name)
                  VALUES
                  (_ext_provider_owner_id, _name)
                  ON CONFLICT (ext_provider_owner_id) DO NOTHING
                  RETURNING provider_owner_id
                  INTO _provider_owner_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
