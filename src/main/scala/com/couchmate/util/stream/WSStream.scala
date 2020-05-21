package com.couchmate.util.stream

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.couchmate.data.wire.{IncomingWSMessage, OutgoingWSMessage, WireMessage}
import play.api.libs.json.Json

import scala.util.{Success, Try}

object WSStream {

  def source(src: Source[Message, NotUsed]): Source[IncomingWSMessage, NotUsed] =
    src.filter(_.isText)
     .flatMapConcat(_.asTextMessage.getStreamedText)
     .map(text => Try(
       Json.parse(text).as[WireMessage]
     ))
     .collect {
       case Success(msg) => IncomingWSMessage(msg)
     }

  def sink(snk: Sink[Message, NotUsed]): Sink[OutgoingWSMessage, NotUsed] =
    Flow[OutgoingWSMessage]
      .map {
        case OutgoingWSMessage(message) => TextMessage(
          Json.toJson(message).toString
        )
      }.to(snk)

}
