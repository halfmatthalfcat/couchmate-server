package com.couchmate.data.db

import com.couchmate.data.models._
import com.couchmate.data.thirdparty.gracenote.{GracenoteAiring, GracenoteProgramType}
import com.couchmate.util.slick.UUIDPlainImplicits
import com.github.tminglei.slickpg._
import enumeratum.{Enum, EnumEntry, SlickEnumPlainSqlSupport, SlickEnumSupport}
import play.api.libs.json.{JsValue, Json}
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities
import slick.migration.api.PostgresDialect

import scala.reflect.ClassTag

trait PgProfile
    extends ExPostgresProfile
    with PgArraySupport
    with PgDateSupport
    with PgDate2Support
    with PgPlayJsonSupport
    with array.PgArrayJdbcTypes
    with SlickEnumSupport {
  override val pgjson: String = "jsonb"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api = new API { }

  trait API
      extends super.API
      with ArrayImplicits
      with DateTimeImplicits
      with JsonImplicits {
    implicit val dialect: PostgresDialect =
      new PostgresDialect

    private[this] def enumMappedColumn[E <: EnumEntry](enum: Enum[E])(
        implicit classTag: ClassTag[E],
    ): BaseColumnType[E] =
      MappedColumnType.base[E, String](
        { _.entryName.toLowerCase },
        { enum.lowerCaseNamesToValuesMap },
      )

    implicit val roomActivityTypeMapper = enumMappedColumn(RoomActivityType)
    implicit val roomStatusTypeMapper = enumMappedColumn(RoomStatusType)
    implicit val userTypeMapper = enumMappedColumn(UserRole)
    implicit val userActivityTypeMapper = enumMappedColumn(UserActivityType)
    implicit val userExtTypeMapper = enumMappedColumn(UserExtType)
    implicit val gnProgramTypeMapper = enumMappedColumn(GracenoteProgramType)

    implicit val playJsonArrayTypeMapper =
      new AdvancedArrayJdbcType[JsValue](
        pgjson,
        (s) => utils.SimpleArrayUtils.fromString[JsValue](Json.parse)(s).orNull,
        (v) => utils.SimpleArrayUtils.mkString[JsValue](b => Json.stringify(Json.toJson(b)))(v))
        .to(_.toSeq)

    implicit val airingSeqJsonMapper =
      new AdvancedArrayJdbcType[GracenoteAiring](
        pgjson,
        (s) =>
          utils.SimpleArrayUtils
            .fromString[GracenoteAiring](Json.parse(_).as[GracenoteAiring])(s)
            .orNull,
        (v) => utils.SimpleArrayUtils.mkString[GracenoteAiring](b => Json.stringify(Json.toJson(b)))(v)
      ).to(_.toSeq)
  }

  val plainAPI = new API
    with PlayJsonPlainImplicits
    with SimpleArrayPlainImplicits
    with Date2DateTimePlainImplicits
    with UUIDPlainImplicits
    with SlickEnumPlainSqlSupport {
    implicit val roomStatusSetParameter = setParameterForEnum(RoomStatusType)
    implicit val roomActivitySetParameter = setParameterForEnum(RoomActivityType)

    implicit val roomStatusGetResult = getResultForEnum(RoomStatusType)
  }
}

object PgProfile extends PgProfile
