package com.couchmate

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/plain`}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCode}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.Future
import scala.util.{Failure, Success}

package object api
  extends LazyLogging {

  def asyncWithStatus(block: => Future[Int]): Route = {
    onComplete(block) {
      case Success(code) => complete(StatusCode.int2StatusCode(code))
      case Failure(ex) =>
        logger.error("Controller Error", ex)
        complete(StatusCode.int2StatusCode(500))
    }
  }

  def asyncWithBody[F: OFormat](block: => Future[Either[(Int, F), (Int, String)]]): Route = {
    onComplete(block) {
      case Success(Left((status, body))) =>
        complete(HttpResponse(
          status,
          entity = HttpEntity(
            `application/json`,
            Json.toJson[F](body).toString(),
          ),
        ))
      case Success(Right((status, ex))) =>
        complete(HttpResponse(
          status,
          entity = HttpEntity(ex),
        ))
      case Failure(ex) =>
        logger.error("Controller Error", ex)
        complete(StatusCode.int2StatusCode(500))
    }
  }

}
