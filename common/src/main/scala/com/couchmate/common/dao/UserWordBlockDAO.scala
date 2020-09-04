package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserWordBlock
import com.couchmate.common.tables.UserWordBlockTable

import scala.concurrent.{ExecutionContext, Future}

trait UserWordBlockDAO {

  def getUserWordBlocks(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[String]] =
    db.run(UserWordBlockDAO.getUserWordBlocks(userId))

  def addUserWordBlock(userWordBlock: UserWordBlock)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Boolean] =
    db.run(UserWordBlockDAO.addUserWordBlock(userWordBlock))
}

object UserWordBlockDAO {
  private[this] lazy val getUserWordBlocksQuery = Compiled { (userId: Rep[UUID]) =>
    UserWordBlockTable.table.filter(_.userId === userId).map(_.word)
  }

  private[common] def getUserWordBlocks(userId: UUID): DBIO[Seq[String]] =
    getUserWordBlocksQuery(userId).result

  private[common] def addUserWordBlock(userWordBlock: UserWordBlock)(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    (UserWordBlockTable.table += userWordBlock) map {
      case 1 => true
      case _ => false
    }
}
