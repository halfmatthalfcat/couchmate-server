package com.couchmate.data.db.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserExtTable
import com.couchmate.data.models.UserExt

import scala.concurrent.{ExecutionContext, Future}

trait UserExtDAO {

  def getUserExt(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserExt]] =
    db.run(UserExtDAO.getUserExt(userId))

  def getUserExt$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Option[UserExt], NotUsed] =
    Slick.flowWithPassThrough(UserExtDAO.getUserExt)

  def upsertUserExt(userExt: UserExt)(
    implicit
    db: Database
  ) = db.run(UserExtDAO.upsertUserExt(userExt))

  def upsertUserExt$()(
    implicit
    session: SlickSession
  ) = Slick.flowWithPassThrough(UserExtDAO.upsertUserExt)
}

object UserExtDAO {
  private[this] lazy val getUserExtQuery = Compiled { (userId: Rep[UUID]) =>
    UserExtTable.table.filter(_.userId === userId)
  }

  private[db] def getUserExt(userId: UUID): DBIO[Option[UserExt]] =
    getUserExtQuery(userId).result.headOption

  private[db] def upsertUserExt(userExt: UserExt) =
    (UserExtTable.table returning UserExtTable.table).insertOrUpdate(userExt)
}
