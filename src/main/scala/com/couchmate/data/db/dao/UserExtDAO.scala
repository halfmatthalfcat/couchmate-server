package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserExtTable
import com.couchmate.data.models.UserExt

import scala.concurrent.{ExecutionContext, Future}

trait UserExtDAO {

  def getUserExt(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserExt]] = {
    db.run(UserExtDAO.getUserExt(userId).result.headOption)
  }

  // TODO how to not get after insert
  def upsertUserExt(userExt: UserExt)(
    implicit
    db: Database
  ) = {
    db.run((UserExtTable.table returning UserExtTable.table).insertOrUpdate(userExt))
  }

}

object UserExtDAO {
  private[dao] lazy val getUserExt = Compiled { (userId: Rep[UUID]) =>
    UserExtTable.table.filter(_.userId === userId)
  }
}
