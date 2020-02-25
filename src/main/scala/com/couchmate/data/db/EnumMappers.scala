package com.couchmate.data.db

import enumeratum._
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.models.{RoomActivityType, UserActivityType, UserExtType, UserRole}

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
