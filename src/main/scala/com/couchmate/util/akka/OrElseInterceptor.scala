package com.couchmate.util.akka

import akka.actor.typed.BehaviorInterceptor.ReceiveTarget
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, BehaviorInterceptor, TypedActorContext}

import scala.annotation.tailrec
import scala.reflect.ClassTag

class OrElseInterceptor[T: ClassTag](
  behaviors: Behavior[T]*
) extends BehaviorInterceptor[T, T] {

  private var handlers: Seq[Behavior[T]] = behaviors

  override def aroundReceive(
    ctx: TypedActorContext[T],
    msg: T,
    target: ReceiveTarget[T],
  ): Behavior[T] = {
    @tailrec def handle(i: Int): Behavior[T] = {
      if (i == behaviors.size) {
        target(ctx, msg)
      } else {
        val next: Behavior[T] =
          Behavior.interpretMessage(behaviors(i), ctx, msg)
        if (Behavior.isUnhandled(next)) {
          handle(i + 1)
        } else if (!Behavior.isAlive(next)) {
          next
        } else {
          handlers = handlers.updated(
            i,
            Behavior.canonicalize(next, handlers(i), ctx)
          )
          Behaviors.same
        }
      }
    }

    handle(0)
  }

  override def isSame(other: BehaviorInterceptor[Any, Any]): Boolean = {
    other.isInstanceOf[OrElseInterceptor[_]]
  }
}
