package com.couchmate.thirdparty.gracenote.provider

import akka.NotUsed
import akka.stream.ClosedShape
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import com.couchmate.services.thirdparty.gracenote.GracenoteService

object Provider {
  def providerGraph(zipCode: String)(
    implicit
    gnService: GracenoteService,
  ) = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val in = gnService.getProviders(zipCode)
      val out = Sink.ignore

      in ~>

      ClosedShape
    })
}
