package com.couchmate.thirdparty.gracenote.provider

import akka.NotUsed
import akka.stream.{ClosedShape, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import com.couchmate.data.models.Provider
import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.data.schema.ProviderDAO
import com.couchmate.data.thirdparty.gracenote.GracenoteProvider
import com.couchmate.services.thirdparty.gracenote.GracenoteService

import scala.concurrent.ExecutionContext

object ProviderIngestor {
  private[this] def getProvider(
    gracenoteProvider: GracenoteProvider,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
  ) =
    Source
      .future(ProviderDAO.getProviderForSourceAndExt(1L, gracenoteProvider.lineupId))
      .flatMapConcat {
        case Some(provider) => Source.single(provider)
        case None => Source.future(ProviderDAO.upsertProvider(Provider(
          providerId = None,
          sourceId = 1L,
          extId = gracenoteProvider.lineupId,
          `type` = Some(gracenoteProvider.`type`),
          location = Some(gracenoteProvider.location),
          name = gracenoteProvider.name,
        )))
      }

  def providerGraph(zipCode: String)(
    implicit
    gnService: GracenoteService,
    db: Database,
  ) =
    RunnableGraph.fromGraph(
      GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits._

        val in = builder.add(Source[Seq[GracenoteProvider]])
        val out = Sink.ignore

        in ~>

        FlowShape(???, ???)
      }
    )
}
