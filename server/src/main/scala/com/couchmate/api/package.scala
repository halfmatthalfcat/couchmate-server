package com.couchmate

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/plain`}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCode}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.CompletionStrategy
import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import com.couchmate.api.sse.SseActor
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

package object api
  extends LazyLogging {

//  def handleSseConnection(roomId: Long)(
//    implicit
//    actorSystem: ActorSystem[Nothing],
//    executionContext: ExecutionContext,
//  ): Source[ServerSentEvent, NotUsed] = {
//    val connectedWsActor: ActorRef = actorSystem.actorOf(Props(new SseActor(roomId)))
//
//    Source
//      .actorRefWithBackpressure(
//        ackMessage = SseActor.Ack,
//        { case _: Success[_] => CompletionStrategy.immediately },
//        PartialFunction.empty,
//      )
//      .mapMaterializedValue { outgoingActor =>
//        connectedWsActor ! SseActor.Connected(outgoingActor)
//        NotUsed
//      }
//      .keepAlive(1 second, () => ServerSentEvent.heartbeat)
//      .watchTermination() { (m, f) =>
//        f.onComplete {
//          case Success(Done) => connectedWsActor ! SseActor.Complete
//          case Failure(ex) => connectedWsActor ! SseActor.Failure(ex)
//        }
//        m
//      }
//  }

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
