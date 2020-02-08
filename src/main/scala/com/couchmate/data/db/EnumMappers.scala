package com.couchmate.data.db

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
