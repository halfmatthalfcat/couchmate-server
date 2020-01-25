package com.couchmate.data.schema

import com.couchmate.data.models._
import com.github.tminglei.slickpg._
import enumeratum.{Enum, EnumEntry}
import play.api.libs.json.{JsValue, Json}
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities
import slick.migration.api.PostgresDialect

import scala.reflect.ClassTag

trait PgProfile
  extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support
  with PgPlayJsonSupport
  with array.PgArrayJdbcTypes {
  override val pgjson: String = "jsonb"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api: API = new API {}
  val plainAPI: API = new API with PlayJsonPlainImplicits

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
    implicit val userTypeMapper = enumMappedColumn(UserType)
    implicit val userActivityTypeMapper = enumMappedColumn(UserActivityType)
    implicit val userExtTypeMapper = enumMappedColumn(UserExtType)

    implicit val playJsonArrayTypeMapper: DriverJdbcType[Seq[JsValue]] =
      new AdvancedArrayJdbcType[JsValue](pgjson,
        (s) => utils.SimpleArrayUtils.fromString[JsValue](Json.parse)(s).orNull,
        (v) => utils.SimpleArrayUtils.mkString[JsValue](_.toString())(v)
      ).to(_.toSeq)

    implicit val airingSeqJsonMapper: DriverJdbcType[Seq[Airing]] =
      new AdvancedArrayJdbcType[Airing](pgjson,
        (s) => utils.SimpleArrayUtils.fromString[Airing](Json.parse(_).as[Airing])(s).orNull,
        (v) => utils.SimpleArrayUtils.mkString[Airing](_.toString())(v)
      ).to(_.toSeq)
  }
}

object PgProfile extends PgProfile
