package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserMetaTable
import com.couchmate.data.models.UserMeta

import scala.concurrent.{ExecutionContext, Future}

class UserMetaDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getUserMeta(userId: UUID): Future[Option[UserMeta]] = {
    db.run(UserMetaDAO.getUserMeta(userId).result.headOption)
  }

  // TODO how to not get after insert
  def upsertUserMeta(userMeta: UserMeta): Future[UserMeta] =
    db.run(((UserMetaTable.table returning UserMetaTable.table).insertOrUpdate(userMeta) flatMap {
      case None => UserMetaDAO.getUserMeta(userMeta.userId).result.head
      case Some(um) => UserMetaDAO.getUserMeta(um.userId).result.head
    }).transactionally)

}

object UserMetaDAO {
  private[dao] lazy val getUserMeta = Compiled { (userId: Rep[UUID]) =>
    UserMetaTable.table.filter(_.userId === userId)
  }
}
