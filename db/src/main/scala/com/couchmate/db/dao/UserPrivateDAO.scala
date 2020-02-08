package com.couchmate.db.dao

import java.util.UUID

import com.couchmate.common.models.UserPrivate
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.UserPrivateQueries
import com.couchmate.db.table.UserPrivateTable

import scala.concurrent.{ExecutionContext, Future}

class UserPrivateDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends UserPrivateQueries {

  def getUserPrivate(userId: UUID): Future[Option[UserPrivate]] = {
    db.run(super.getUserPrivate(userId).result.headOption)
  }

  def upsertUserPrivate(userPrivate: UserPrivate): Future[UserPrivate] =
    db.run(((UserPrivateTable.table returning UserPrivateTable.table).insertOrUpdate(userPrivate) flatMap {
      case None => super.getUserPrivate(userPrivate.userId).result.head
      case Some(up) => super.getUserPrivate(up.userId).result.head
    }).transactionally)

}
