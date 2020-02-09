package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserExtTable
import com.couchmate.data.models.UserExt

import scala.concurrent.{ExecutionContext, Future}

class UserExtDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getUserExt(userId: UUID): Future[Option[UserExt]] = {
    db.run(UserExtDAO.getUserExt(userId).result.headOption)
  }

  // TODO how to not get after insert
  def upsertUserExt(userExt: UserExt): Future[UserExt] =
    db.run(((UserExtTable.table returning UserExtTable.table).insertOrUpdate(userExt) flatMap {
      case None => UserExtDAO.getUserExt(userExt.userId).result.head
      case Some(ue) => UserExtDAO.getUserExt(ue.userId).result.head
    }).transactionally)

}

object UserExtDAO {
  private[dao] lazy val getUserExt = Compiled { (userId: Rep[UUID]) =>
    UserExtTable.table.filter(_.userId === userId)
  }
}
