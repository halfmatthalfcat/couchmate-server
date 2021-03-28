package com.couchmate.common.models.api.grid

import com.couchmate.common.models.data.RoomStatusType
import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class GridAiringDynamic(
  airingId: String,
  status: RoomStatusType,
  count: Long,
  following: Long,
)

object GridAiringDynamic {
  implicit val format: Format[GridAiringDynamic] = Json.format[GridAiringDynamic]
  implicit val rowParser: GetResult[GridAiringDynamic] = RowParser[GridAiringDynamic]
}
