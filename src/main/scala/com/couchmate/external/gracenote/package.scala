package com.couchmate.external

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.model.headers.RawHeader

package object gracenote {
  def makeGracenoteRequest(
    host: String,
    key: String,
    path: Seq[String],
    qs: Map[String, Option[String]]
  ): HttpRequest = {
    val defaultQs = Map(
      "api_key" -> Some(key)
    )

    val queryString: String =
      (defaultQs ++ qs).collect {
        case (key, Some(value)) => key -> value
      }.foldLeft("?") {
        case (acc, (key, value)) => acc + s"$key=$value&"
      }.dropRight(1)

    HttpRequest(
      method = HttpMethods.GET,
      uri = s"http://$host/${path.mkString("/")}$queryString",
      headers = Seq(
        RawHeader("Accept-encoding", "gzip"),
      ),
    )
  }
}
