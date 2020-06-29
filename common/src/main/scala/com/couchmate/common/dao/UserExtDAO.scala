package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import java.util.UUID

import com.couchmate.common.models.data.UserExt
import com.couchmate.common.tables.UserExtTable

import scala.concurrent.Future

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

  private[common] def getUserExt(userId: UUID): DBIO[Option[UserExt]] =
    getUserExtQuery(userId).result.headOption

  private[common] def upsertUserExt(userExt: UserExt) =
    (UserExtTable.table returning UserExtTable.table).insertOrUpdate(userExt)
}
