package com.couchmate.db.dao

import java.util.UUID

import com.couchmate.common.models.UserMeta
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.UserMetaQueries
import com.couchmate.db.table.UserMetaTable

import scala.concurrent.{ExecutionContext, Future}

class UserMetaDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends UserMetaQueries {

  def getUserMeta(userId: UUID): Future[Option[UserMeta]] = {
    db.run(super.getUserMeta(userId).result.headOption)
  }

  // TODO how to not get after insert
  def upsertUserMeta(userMeta: UserMeta): Future[UserMeta] =
    db.run(((UserMetaTable.table returning UserMetaTable.table).insertOrUpdate(userMeta) flatMap {
      case None => super.getUserMeta(userMeta.userId).result.head
      case Some(um) => super.getUserMeta(um.userId).result.head
    }).transactionally)

}
