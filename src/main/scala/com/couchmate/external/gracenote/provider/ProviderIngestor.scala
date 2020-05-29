//package com.couchmate.external.gracenote.provider
//
//import akka.NotUsed
//import akka.actor.typed.ActorRef
//import akka.stream.{ClosedShape, FlowShape, Materializer, SinkShape}
//import akka.stream.alpakka.slick.scaladsl.SlickSession
//import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
//import akka.stream.typed.scaladsl.{ActorFlow, ActorSink}
//import akka.util.Timeout
//import com.couchmate.data.db.dao.{ProviderDAO, ProviderOwnerDAO}
//import com.couchmate.data.models.Provider
//import com.couchmate.external.gracenote.models.GracenoteProvider
//
//import scala.concurrent.{ExecutionContext, Future}
//
//object ProviderIngestor
//  extends ProviderDAO
//  with ProviderOwnerDAO {
//  sealed trait Command
//  sealed trait IncomingCommand extends Command
//  sealed trait OutgoingCommand extends Command
//
//  case class Ingest(
//    zipCode: String,
//    country: Option[String],
//    providers: Seq[GracenoteProvider]
//  ) extends IncomingCommand
//  case object Ingested extends OutgoingCommand
//  case object Complete extends OutgoingCommand
//  case class Error(err: Throwable) extends OutgoingCommand
//
//  case class IngestState(
//    zipCode: String,
//    country: Option[String],
//    provider: GracenoteProvider
//  )
//
//  def apply(
//    source: Source[IncomingCommand, ActorRef[IncomingCommand]],
//    sink: Sink[OutgoingCommand, NotUsed]
//  )(
//    implicit
//    timeout: Timeout,
//    ec: ExecutionContext,
//    session: SlickSession
//  ): RunnableGraph[ActorRef[IncomingCommand]] = RunnableGraph.fromGraph(GraphDSL.create(source, sink)((source, _) => source) {
//      implicit b: GraphDSL.Builder[ActorRef[IncomingCommand]] =>
//        (source, sink) =>
//          import GraphDSL.Implicits._
//
//          val filterCommand: FlowShape[IncomingCommand, Ingest] =
//            b.add(Flow[IncomingCommand].collectType[Ingest])
//          val emitProviders: FlowShape[Ingest, IngestState] =
//            b.add(Flow[Ingest]
//              .map(ingest => ingest.providers.map(provider => IngestState(
//                provider = provider,
//                zipCode = ingest.zipCode,
//                country = ingest.country
//              )))
//              .mapConcat(identity)
//            )
//
//          source ~> filterCommand ~> emitProviders.in
//
//          emitProviders.out ~> getProviderOwnerFromGracenote$
//
//      ClosedShape
//  })
//}
//
//class ProviderIngestor(
//  gnService: GracenoteService,
//  database: CMDatabase,
//) {
//  import database._
//
//  def ingestProvider(
//    zipCode: Option[String],
//    country: Option[String],
//    gracenoteProvider: GracenoteProvider,
//  )(
//    implicit
//    ec: ExecutionContext
//  ): Future[Provider] = for {
//    owner <- providerOwner.getProviderOwnerFromGracenote(
//      gracenoteProvider,
//      country,
//    )
//    provider <- provider.getProviderFromGracenote(
//      gracenoteProvider,
//      owner,
//      country,
//    )
//    _ <- zipCode.fold(Future.successful()) { zipCode: String =>
//      zipProvider.getZipProviderFromGracenote(
//        zipCode,
//        provider,
//      ) map (_ => ())
//    }
//  } yield provider
//
//  def ingestProviders(
//    zipCode: String,
//    country: Option[String],
//  )(implicit ec: ExecutionContext, mat: Materializer): Source[Provider, NotUsed] =
//    Source
//      .future(gnService.getProviders(zipCode, country))
//      .mapConcat(identity)
//      .filter(!_.name.toLowerCase.contains("c-band"))
//      .mapAsync(1)(ingestProvider(Some(zipCode), country, _))
//}