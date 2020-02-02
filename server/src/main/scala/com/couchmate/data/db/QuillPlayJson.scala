package com.couchmate.data.db

import play.api.libs.json.{Json, OWrites, Reads}

trait QuillPlayJson[T, N] {
  val context: CMContext
  import context._

  implicit def jsonDecoder[R: Reads]: Decoder[R] =
    decoder((index, row) =>
      Json.parse(row.getObject(index).toString).as[R]
    )

  implicit def jsonEncoder[W: OWrites]: Encoder[W] =
    encoder(java.sql.Types.OTHER, (index, value, row) =>
      row.setObject(index, Json.toJson(value).toString, java.sql.Types.OTHER)
    )

}
