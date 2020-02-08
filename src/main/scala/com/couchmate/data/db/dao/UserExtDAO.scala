package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.UserExtQueries
import com.couchmate.data.db.table.UserExtTable
import com.couchmate.data.models.UserExt

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
