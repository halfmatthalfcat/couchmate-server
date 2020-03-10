package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserPrivateTable
import com.couchmate.data.models.UserPrivate

import scala.concurrent.{ExecutionContext, Future}

class UserPrivateDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getUserPrivate(userId: UUID): Future[Option[UserPrivate]] = {
    db.run(UserPrivateDAO.getUserPrivate(userId).result.headOption)
  }

  def upsertUserPrivate(userPrivate: UserPrivate) =
    db.run((UserPrivateTable.table returning UserPrivateTable.table).insertOrUpdate(userPrivate))

}

object UserPrivateDAO {
  private[dao] lazy val getUserPrivate = Compiled { (userId: Rep[UUID]) =>
    UserPrivateTable.table.filter(_.userId === userId)
  }
}
