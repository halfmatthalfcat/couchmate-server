package com.couchmate.api.sse

import akka.actor.typed.{Behavior, BehaviorInterceptor, TypedActorContext}

private[sse] class SSEHandlerInterceptor[T](
  handler: PartialFunction[SSEHandler.Command, T],
) extends BehaviorInterceptor[SSEHandler.Command, T] {
  import BehaviorInterceptor._

  override def aroundReceive(ctx: TypedActorContext[SSEHandler.Command], msg: SSEHandler.Command, target: ReceiveTarget[T]): Behavior[T] = {
    target(ctx, handler(msg))
  }
}
