package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._

import scala.concurrent.ExecutionContext

class GridDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getGrid

}
