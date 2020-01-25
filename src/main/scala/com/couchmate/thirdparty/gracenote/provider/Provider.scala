package com.couchmate.thirdparty.gracenote.provider

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.SlickSession
import akka.stream.{ClosedShape, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, RunnableGraph, Sink, Source}
import com.couchmate.data.models.Provider
import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.data.schema.ProviderDAO
import com.couchmate.data.thirdparty.gracenote.GracenoteProvider
import com.couchmate.services.thirdparty.gracenote.GracenoteService

import scala.concurrent.ExecutionContext

object ProviderIngestor {

  private[this] def ingestProvider()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[GracenoteProvider, Provider, NotUsed] = Flow[GracenoteProvider].flatMapConcat {
    case GracenoteProvider(lineupId, name, location, t) => Source
      .single((1L, lineupId))
      .via(ProviderDAO.getProviderForSourceAndExt())
      .flatMapConcat {
        case None => Source.single(Provider(
          sourceId = 1L,
          extId = lineupId,
          name = name,
          `type` = Some(t),
          location = Some(location),
        )).via(ProviderDAO.upsertProvider())
        case Some(provider) => Source.single(provider)
      }
  }

  def ingestProviders()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[String, Provider, NotUsed] = Flow[String]
    .
}
