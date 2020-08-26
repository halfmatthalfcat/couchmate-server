package com.couchmate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, optionalHeaderValueByName}
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config

import scala.jdk.CollectionConverters._

package object api {

  def withApiKey(fn: => Route)(
    implicit
    config: Config
  ): Route = {
    val apiKeys: Seq[String] = config
      .getStringList("apiKeys")
      .asScala
      .toSeq

    optionalHeaderValueByName("Authorization") {
      case Some(value) if (
        value.startsWith("apiKey") &&
        apiKeys.contains(value.split("apiKey ")(1)
      )) => fn
      case _ => complete(StatusCodes.Unauthorized)
    }
  }

}
