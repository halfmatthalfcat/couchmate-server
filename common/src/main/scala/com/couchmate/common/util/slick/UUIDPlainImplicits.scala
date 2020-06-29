package com.couchmate.common.util.slick

import java.sql.Types
import java.util.UUID

import com.github.tminglei.slickpg.utils
import slick.jdbc.PositionedResult

trait UUIDPlainImplicits {
  import utils.PlainSQLUtils._

  implicit class PgPositionedResult(val r: PositionedResult) {
    def nextUUID: UUID = r.nextObject().asInstanceOf[UUID]
    def nextUUIDOption: Option[UUID] = r.nextObjectOption().map(_.asInstanceOf[UUID])
  }

  implicit val getUUID = mkGetResult(_.nextUUID)
  implicit val getUUIDOption = mkGetResult(_.nextUUIDOption)
  implicit val setUUID = mkSetParameter[UUID]("uuid", _.toString, sqlType = Types.OTHER)
  implicit val setUUIDOption = mkOptionSetParameter[UUID]("uuid", _.toString, sqlType = Types.OTHER)


}
