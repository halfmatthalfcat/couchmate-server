package com.couchmate.services.cache

import akka.actor.typed.pubsub.Topic.Subscribe
import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.util.akka.extensions.CacheExtension
import scalacache.modes.scalaFuture._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ClusterCacheBuster {
  sealed trait Command
  final case class Bust(cacheKey: String) extends Command

  private final case class Busted(cacheKey: String) extends Command
  private final case class BustFailed(cacheKey: String, err: Throwable) extends Command

  def apply(): Behavior[Command] = Behaviors.supervise(
    Behaviors.setup { ctx: ActorContext[Command] =>
      implicit val ec: ExecutionContext = ctx.executionContext
      val cacheExtension = CacheExtension(ctx.system)

      cacheExtension.cacheTopic ! Subscribe(ctx.self)

      Behaviors.receiveMessage[Command] {
        case Bust(cacheKey) => ctx.pipeToSelf(
          cacheExtension.caffeine.remove(cacheKey)
        ) {
          case Success(_) => Busted(cacheKey)
          case Failure(exception) => BustFailed(cacheKey, exception)
        }
          Behaviors.same
        case Busted(cacheKey) =>
          ctx.log.debug(s"Busted $cacheKey via remote request")
          Behaviors.same
        case BustFailed(cacheKey, err) =>
          ctx.log.error(s"Unable to bust $cacheKey via remote request", err)
          Behaviors.same
      }
    }
  ).onFailure(SupervisorStrategy.restart)
}
