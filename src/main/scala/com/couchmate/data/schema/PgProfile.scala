package com.couchmate.data.schema

import com.couchmate.data.models.Airing
import com.github.tminglei.slickpg._
import play.api.libs.json.{JsValue, Json}

trait PgProfile
  extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support
  with PgPlayJsonSupport
  with array.PgArrayJdbcTypes {
  override val pgjson: String = "jsonb"

  override val api: API = new API {}
  val plainAPI: API = new API with PlayJsonPlainImplicits

  trait API
    extends super.API
    with ArrayImplicits
    with DateTimeImplicits
    with JsonImplicits {
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
