package com.couchmate.common.tables

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserWordBlock
import com.couchmate.common.util.slick.WithTableQuery

class UserWordBlockTable(tag: Tag) extends Table[UserWordBlock](tag, "user_word_block") {
  def userId: Rep[UUID] = column[UUID]("user_id")
  def word: Rep[String] = column[String]("word")

  def * = (
    userId,
    word
  ) <> ((UserWordBlock.apply _).tupled, UserWordBlock.unapply)

  def userWordBlockPK = primaryKey(
    "user_word_block_pk",
    (userId, word)
  )
}

object UserWordBlockTable extends WithTableQuery[UserWordBlockTable] {
  private[couchmate] val table = TableQuery[UserWordBlockTable]
}
