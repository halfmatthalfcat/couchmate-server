package com.couchmate.data.schema

import slick.jdbc.{JdbcCapabilities, PostgresProfile}
import com.github.tminglei.slickpg._
import slick.basic.Capability

trait PgProfile extends PostgresProfile
  with PgDate2Support
  with PgPlayJsonSupport {
  def pgjson = "jsonb"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api: API = MyAPI

  object MyAPI extends API
    with DateTimeImplicits
    with JsonImplicits
}

object PgProfile extends PgProfile
