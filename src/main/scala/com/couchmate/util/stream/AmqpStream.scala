package com.couchmate.util.stream

import java.util.UUID

import akka.{Done, NotUsed}
import akka.actor.typed.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.alpakka.amqp.scaladsl.{AmqpSink, AmqpSource}
import akka.stream.alpakka.amqp._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import akka.util.ByteString
import com.couchmate.data.wire._
import com.couchmate.util.AmqpProvider
import com.rabbitmq.client.Envelope
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object AmqpStream extends LazyLogging {

  def source(
    connectionProvider: AmqpConnectionProvider,
    routes: Set[Route]
  ): Source[IncomingAmqpMessage, NotUsed] = {
    val queueId: String = UUID.randomUUID().toString
    val queueDecl: QueueDeclaration =
      QueueDeclaration(queueId)
        .withAutoDelete(true)
        .withExclusive(true)
        .withDurable(false)

    logger.info(
      s"""Starting AMQP Source:
         |Exchange: ${Exchange.Messages.entryName}
         |Routes: ${routes.map(_.toRoute).mkString(", ")}
         |Queue: ${queueDecl.name}
         |""".stripMargin)

    val bindingDecls: Seq[BindingDeclaration] =
      routes.map(route => BindingDeclaration(
        queueDecl.name,
        Exchange.Messages.entryName,
      ).withRoutingKey(route)).toSeq

    val queue: NamedQueueSourceSettings =
      NamedQueueSourceSettings(
        connectionProvider,
        queueId,
      // The order of these matter
      ).withDeclarations(queueDecl +: bindingDecls)

    AmqpSource.atMostOnceSource(
      queue,
      bufferSize = 10
    )
      .recover {
        case ex: Throwable =>
          logger.debug(ex.toString)
          throw ex
      }
      .filter(_.bytes.nonEmpty)
      .map[String](_.bytes.utf8String)
      .map(msg => Try(
        Json.parse(msg).as[WireMessage]
      ))
      .collect {
        case Success(msg) => IncomingAmqpMessage(msg)
      }
  }

  def sink(
    connectionProvider: AmqpConnectionProvider
  ): Sink[OutgoingAmqpMessage, NotUsed] = {
    val exchange: ExchangeDeclaration =
      ExchangeDeclaration(
        Exchange.Messages.entryName,
        "topic"
      )

    val settings: AmqpWriteSettings =
      AmqpWriteSettings(connectionProvider)
        .withDeclaration(exchange)
        .withExchange(Exchange.Messages.entryName)

    Flow[OutgoingAmqpMessage]
      .map {
        case OutgoingAmqpMessage(message, route) => WriteMessage(
          ByteString(Json.toJson(message).toString)
        ).withRoutingKey(route)
      }
      .to(AmqpSink(settings))
  }
}
