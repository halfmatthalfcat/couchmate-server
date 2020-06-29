package com.couchmate.common.db

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{RoomActivityType, UserActivityType, UserExtType, UserRole}
import enumeratum._

import scala.reflect.ClassTag

trait EnumMappers {

  private[this] def enumMappedColumn[E <: EnumEntry](enum: Enum[E])(
    implicit classTag: ClassTag[E],
  ): BaseColumnType[E] =
    MappedColumnType.base[E, String](
      { _.entryName.toLowerCase },
      { enum.lowerCaseNamesToValuesMap },
    )

  implicit val roomActivityTypeMapper = enumMappedColumn(RoomActivityType)
  implicit val userTypeMapper = enumMappedColumn(UserRole)
  implicit val userActivityTypeMapper = enumMappedColumn(UserActivityType)
  implicit val userExtTypeMapper = enumMappedColumn(UserExtType)

}
