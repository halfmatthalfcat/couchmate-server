package com.couchmate.common.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserPrivate
import com.couchmate.common.tables.UserPrivateTable

import scala.concurrent.Future

trait UserPrivateDAO {

  def getUserPrivate(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserPrivate]] =
    db.run(UserPrivateDAO.getUserPrivate(userId))

  def getUserPrivate$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Option[UserPrivate], NotUsed] =
    Slick.flowWithPassThrough(UserPrivateDAO.getUserPrivate)

  def upsertUserPrivate(userPrivate: UserPrivate)(
    implicit
    db: Database
  ) = db.run(UserPrivateDAO.upsertUserPrivate(userPrivate))

  def upsertUserPrivate$()(
    implicit
    session: SlickSession
  ) = Slick.flowWithPassThrough(UserPrivateDAO.upsertUserPrivate)

}

object UserPrivateDAO {
  private[this] lazy val getUserPrivateQuery = Compiled { (userId: Rep[UUID]) =>
    UserPrivateTable.table.filter(_.userId === userId)
  }

  private[common] def getUserPrivate(userId: UUID): DBIO[Option[UserPrivate]] =
    getUserPrivateQuery(userId).result.headOption

  private[common] def upsertUserPrivate(userPrivate: UserPrivate) =
    (UserPrivateTable.table returning UserPrivateTable.table).insertOrUpdate(userPrivate)
}
