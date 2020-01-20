package com.couchmate.data.schema

import com.couchmate.data.models.{RoomActivityType, UserActivityType, UserExtType, UserType}
import com.couchmate.data.schema.PgProfile.api._
import enumeratum.{Enum, EnumEntry}

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
  implicit val userTypeMapper = enumMappedColumn(UserType)
  implicit val userActivityTypeMapper = enumMappedColumn(UserActivityType)
  implicit val userExtTypeMapper = enumMappedColumn(UserExtType)

}
