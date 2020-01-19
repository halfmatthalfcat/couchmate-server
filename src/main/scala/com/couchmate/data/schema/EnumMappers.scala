package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.{RoomActivityType, UserActivityType, UserType}
import enumeratum.{Enum, EnumEntry}

trait EnumMappers {

  private[this] def enumMappedColumn[E <: EnumEntry](enum: Enum[E]): BaseColumnType[E] =
    MappedColumnType.base[E, String](
      t => t.entryName.toLowerCase,
      enum.lowerCaseNamesToValuesMap,
    )

  implicit val roomActivityTypeMapper = enumMappedColumn(RoomActivityType)
  implicit val userTypeMapper = enumMappedColumn(UserType)
  implicit val userActivityTypeMapper = enumMappedColumn(UserActivityType)

}
