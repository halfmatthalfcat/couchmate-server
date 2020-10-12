package com.couchmate.services.user.commands

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder}
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{State, UserContextSet}
import com.couchmate.services.user.context.UserContext

object EmptyCommands {
  private[user] def setUserContext(userContext: UserContext)(
    implicit
    ctx: ActorContext[PersistentUser.Command]
  ): Effect[UserContextSet, State] =
    Effect
      .persist(UserContextSet(userContext))
      .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} context set"))
      .thenUnstashAll()

  private[user] def setUserContextFailed(exception: Throwable)(
    implicit
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] =
    Effect
      .stop()
      .thenRun((_: State) => ctx.log.debug(s"Couldn't find user context: ${exception.getMessage}"))

}
