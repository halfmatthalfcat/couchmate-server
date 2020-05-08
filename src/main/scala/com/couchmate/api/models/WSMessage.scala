package com.couchmate.api.models

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import play.api.libs.json.{Format, Json, OFormat, Writes}

case class WSMessage[T: Writes](
  `type`: String,
  data: Option[T],
)

object WSMessage {
  implicit def format[T](implicit format: Format[T]): Format[WSMessage[T]] =
    Json.format[WSMessage[T]]

  implicit def toMessage[T](message: WSMessage[T])(implicit format: Format[T]): Message =
    TextMessage(Json.toJson(message).toString)
}
