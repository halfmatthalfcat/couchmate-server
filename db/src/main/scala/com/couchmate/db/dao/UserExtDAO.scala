package com.couchmate.db.dao

import java.util.UUID

import com.couchmate.common.models.UserExt
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.UserExtQueries
import com.couchmate.db.table.UserExtTable

import scala.concurrent.{ExecutionContext, Future}

class UserExtDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends UserExtQueries {

  def getUserExt(userId: UUID): Future[Option[UserExt]] = {
    db.run(super.getUserExt(userId).result.headOption)
  }

  // TODO how to not get after insert
  def upsertUserExt(userExt: UserExt): Future[UserExt] =
    db.run(((UserExtTable.table returning UserExtTable.table).insertOrUpdate(userExt) flatMap {
      case None => super.getUserExt(userExt.userId).result.head
      case Some(ue) => super.getUserExt(ue.userId).result.head
    }).transactionally)

}
