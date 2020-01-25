package com.couchmate.api.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.alpakka.slick.scaladsl.SlickSession
import akka.util.Timeout
import com.couchmate.data.models.Source
import com.couchmate.data.schema.SourceDAO
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

object SourceRoutes extends PlayJsonSupport {
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def apply()(
    implicit
    actorSystem: ActorSystem[Nothing],
    timeout: Timeout,
    session: SlickSession,
  ): Route = {
    pathPrefix("source") {
      pathEndOrSingleSlash {
        post {
          entity(asSourceOf[Source]) { source =>
            val entity = source
              .via(SourceDAO.addSource())

            complete(entity)
          }
        }
      }
    }
  }
}
