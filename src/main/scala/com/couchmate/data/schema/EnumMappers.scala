package com.couchmate.data.schema

import com.couchmate.data.models.{RoomActivityType, UserType}
import enumeratum.SlickEnumSupport
import slick.jdbc.PostgresProfile

trait EnumMappers extends SlickEnumSupport {
  val profile: PostgresProfile

  implicit lazy val userTypeMapper =
    mappedColumnTypeForLowercaseEnum(UserType)
  implicit lazy val roomActivityTypeMapper =
    mappedColumnTypeForLowercaseEnum(RoomActivityType)
}
