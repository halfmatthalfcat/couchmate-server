package com.couchmate.api.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.api._
import com.couchmate.data.models.Source
import com.couchmate.services.data.source.SourceService
import com.couchmate.services.data.source.SourceService.AddSource
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object SourceRoutes extends PlayJsonSupport {
  def apply(
    sourceServiceRouter: ActorRef[SourceService.Command],
  )(
    implicit
    actorSystem: ActorSystem[Nothing],
    timeout: Timeout,
    ec: ExecutionContext,
  ): Route = {
    pathPrefix("source") {
      pathEndOrSingleSlash {
        post {
          entity(as[Source]) { source =>
            asyncWithBody[Source] {
              sourceServiceRouter ask { ref =>
                AddSource(source, ref)
              }
            }
          }
        }
      }
    }
  }
}
