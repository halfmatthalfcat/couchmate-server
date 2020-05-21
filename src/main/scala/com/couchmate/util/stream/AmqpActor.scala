package com.couchmate.util.stream

/**
 * AMQP Actor
 * Handles all of the message routing and failure semantics
 * internally, letting the spawning (parent) actor assume it
 * will always reliably deliver messages
 */

import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.alpakka.amqp.{AmqpCachedConnectionProvider, AmqpConnectionProvider, AmqpCredentials, AmqpDetailsConnectionProvider}
import akka.stream.scaladsl.Sink
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.stream.typed.scaladsl.ActorSource
import com.couchmate.data.wire.{IncomingAmqpMessage, OutgoingAmqpMessage, Route}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object AmqpActor extends LazyLogging {
  sealed trait Command

  /* External API */

  case class  Incoming(message: IncomingAmqpMessage) extends Command
  case class  Outgoing(message: OutgoingAmqpMessage) extends Command

  case class  AddRoutes(routes: Route*)    extends Command
  case class  RemoveRoutes(routes: Route*) extends Command

  /* Internal API */

  private case class  Connected(actorRef: ActorRef[Command]) extends Command

  private case object Stop                          extends Command
  private case object Complete                      extends Command
  private case class  SinkFailure(err: Throwable)   extends Command
  private case class  SourceFailure(err: Throwable) extends Command

  /* Implementation */

  def apply(actorRef: ActorRef[Command], routes: Set[Route]): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      implicit val ec: ExecutionContext = ctx.executionContext
      val config: Config = ConfigFactory.load()
      val connectionProvider: AmqpConnectionProvider =
        AmqpCachedConnectionProvider(
          AmqpDetailsConnectionProvider(
            config.getString("amqp.host"),
            config.getInt("amqp.port")
          ).withCredentials(
            AmqpCredentials(
              config.getString("amqp.credentials.username"),
              config.getString("amqp.credentials.password"),
            )
          ).withVirtualHost(
            config.getString("amqp.vhost")
          )
        )

      logger.info(s"Starting AMQP Actor with routes: ${routes.mkString(", ")}")

      // Forward incoming messages to the parent actor
      // In our instance, it's usually a websocket actor
      // This stream should never complete unless the underlying
      // AMQP connection goes out, in that case, we should restart
      AmqpStream.source(connectionProvider, routes)
        .map(Incoming)
        .runWith(Sink.foreach(ctx.self ! _))
        .onComplete {
          case Success(Done) =>
            logger.debug(s"Source completed")
          case Failure(exception) =>
            logger.debug(s"$exception")
            ctx.self ! SourceFailure(exception)
        }

      // Spawning the outgoing AMQP actor
      // This child actor shouldn't really
      ActorSource.actorRef[Command](
        PartialFunction.empty,
        PartialFunction.empty,
        10,
        OverflowStrategy.dropNew,
      ).mapMaterializedValue { outgoingActor: ActorRef[Command] =>
        ctx.self ! Connected(outgoingActor)
        NotUsed
      }.watchTermination() { (m, f) =>
        f.onComplete {
          case Success(Done) =>
            logger.debug(s"Sink completed")
          case Failure(ex) =>
            ctx.self ! SinkFailure(ex)
        }
        m
      }.collect {
        case Outgoing(outgoing) => outgoing
      }.runWith(AmqpStream.sink(connectionProvider))

      def run(outgoingRef: Option[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessagePartial {
        /* Public API */
        case incoming: Incoming =>
          actorRef ! incoming
          Behaviors.same

        case outgoing: Outgoing =>
          outgoingRef.fold(()) { ref =>
            ref ! outgoing
          }
          Behaviors.same

        case AddRoutes(addedRoutes @ _*) =>
          outgoingRef.fold(()) { ref =>
            ref ! Complete
          }
          apply(actorRef, routes ++ addedRoutes)

        case RemoveRoutes(removedRoutes @ _*) =>
          outgoingRef.fold(()) { ref =>
            ref ! Complete
          }
          apply(actorRef, routes -- removedRoutes)

        /* Internal API */
        case Connected(actorRef) =>
          run(Some(actorRef))

        case SinkFailure(err) =>
          logger.debug(s"Sink failure: ${err.getMessage}")
          outgoingRef.fold(()) { ref =>
            ref ! Complete
          }
          apply(actorRef, routes)

        case SourceFailure(err) =>
          logger.debug(s"Source failure: ${err.getMessage}")
          outgoingRef.fold(()) { ref =>
            ref ! Complete
          }
          apply(actorRef, routes)

        case Stop =>
          Behaviors.stopped
      }

      run(None)
    }
}
