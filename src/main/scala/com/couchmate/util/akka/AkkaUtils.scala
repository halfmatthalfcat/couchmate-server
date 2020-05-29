package com.couchmate.util.akka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

trait AkkaUtils {

  def compose[T](behaviors: PartialFunction[T, Behavior[T]]*)(command: T): Behavior[T] =
    behaviors match {
      case Seq() => Behaviors.unhandled
      case Seq(head, tail @ _*) => head.applyOrElse(command, compose(tail: _*))
    }

}
