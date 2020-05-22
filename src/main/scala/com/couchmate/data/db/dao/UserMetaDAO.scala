package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserMetaTable
import com.couchmate.data.models.UserMeta

import scala.concurrent.{ExecutionContext, Future}

trait UserMetaDAO {

  def getUserMeta(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserMeta]] = {
    db.run(UserMetaDAO.getUserMeta(userId).result.headOption)
  }

  def emailExists(email: String)(
    implicit
    db: Database
  ): Future[Boolean] = {
    db.run(UserMetaDAO.emailExists(email).result)
  }

  def upsertUserMeta(userMeta: UserMeta)(
    implicit
    db: Database
  ) = {
    db.run((UserMetaTable.table returning UserMetaTable.table).insertOrUpdate(userMeta))
  }

}

object UserMetaDAO {
  private[dao] lazy val getUserMeta = Compiled { (userId: Rep[UUID]) =>
    UserMetaTable.table.filter(_.userId === userId)
  }

  private[dao] lazy val emailExists = Compiled { (email: Rep[String]) =>
    UserMetaTable.table.filter(_.email === email).exists
  }
}
