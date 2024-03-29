package com.couchmate.common.db

import com.couchmate.common.models.data._
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiring, GracenoteProgramType}
import com.couchmate.common.util.slick.UUIDPlainImplicits
import com.github.tminglei.slickpg._
import com.neovisionaries.i18n.CountryCode
import enumeratum.{Enum, EnumEntry, SlickEnumPlainSqlSupport, SlickEnumSupport}
import play.api.libs.json.{JsValue, Json}
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

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
    implicit val showTypeMapper = enumMappedColumn(ShowType)
    implicit val listingJobStatusMapper = enumMappedColumn(ListingJobStatus)
    implicit val userReportMapper = enumMappedColumn(UserReportType)
    implicit val applicationPlatformMapper = enumMappedColumn(ApplicationPlatform)
    implicit val notificationTypeMapper = enumMappedColumn(UserNotificationQueueItemType)
    implicit val providerTypeMapper = enumMappedColumn(ProviderType)

    implicit val countryCodeMappedColumn: BaseColumnType[CountryCode] =
      MappedColumnType.base[CountryCode, String](
        { _.getAlpha3 },
        { CountryCode.getByAlpha3Code }
      )

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

    implicit val doubleTupleMapper: DriverJdbcType[(Double, Double)] =
      new AdvancedArrayJdbcType[Double]("decimal",
        s => utils.SimpleArrayUtils
          .fromString[Double](_.toDouble)(s).orNull,
        v => utils.SimpleArrayUtils.mkString[Double](_.toString)(v)
      ).to(arr => (arr.head, arr(1)), tuple => Seq(tuple._1, tuple._2))
  }

  val plainAPI = new API
    with PlayJsonPlainImplicits
    with SimpleArrayPlainImplicits
    with Date2DateTimePlainImplicits
    with UUIDPlainImplicits
    with SlickEnumPlainSqlSupport {
    implicit val roomStatusSetParameter = setParameterForEnumLowercase(RoomStatusType)
    implicit val roomActivitySetParameter = setParameterForEnumLowercase(RoomActivityType)
    implicit val showTypeSetParameter = setParameterForEnumLowercase(ShowType)
    implicit val providerTypeSetParameter = setParameterForEnumLowercase(ProviderType)

    implicit val roomStatusGetResult = getResultForEnumLowercase(RoomStatusType)
    implicit val showTypeGetResult = getResultForEnumLowercase(ShowType)
    implicit val providerTypeGetResult = getResultForEnumLowercase(ProviderType)

    implicit val airingSeqSetParameter =
      utils.PlainSQLUtils.mkArraySetParameter[GracenoteAiring]("jsonb",
        v => Json.stringify(Json.toJson(v))
      )
  }
}

object PgProfile extends PgProfile
