//package com.couchmate.api.ws
//
//import akka.actor.typed.{Behavior, BehaviorInterceptor, TypedActorContext}
//import com.couchmate.util.stream.WSStream
//
///**
// * We need a way to forward messages from the materialized
// * intermediate actor that is actually handling messages (WSHandler) to
// * the actor doing the actual work. Because of the typed nature of
// * actors, this Interceptor acts similarly to the MessageAdapter but
// * at a higher level.
// *
// * @see https://doc.akka.io/api/akka/current/akka/actor/typed/BehaviorInterceptor.html
// */
//private[ws] class WSHandlerInterceptor[T](
//  handler: PartialFunction[WSStream.Command, T],
//) extends BehaviorInterceptor[WSStream.Command, T] {
//  import BehaviorInterceptor._
//
//  override def aroundReceive(ctx: TypedActorContext[WSStream.Command], msg: WSStream.Command, target: ReceiveTarget[T]): Behavior[T] = {
//    target(ctx, handler(msg))
//  }
//}