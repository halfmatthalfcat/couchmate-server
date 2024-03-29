package com.couchmate.util.akka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

trait AkkaUtils {

  def compose[T](behaviors: PartialFunction[T, Behavior[T]]*)(command: T): Behavior[T] =
    behaviors match {
      case Nil => Behaviors.unhandled
      case Seq(head, tail @ _*) => head.applyOrElse(command, compose(tail: _*))
    }

  def chain[T](behaviors: PartialFunction[T, Behavior[T]]*): PartialFunction[T, Behavior[T]] =
    behaviors.fold(PartialFunction.empty)(_ orElse _) orElse {
      case _ => Behaviors.unhandled
    }
}
