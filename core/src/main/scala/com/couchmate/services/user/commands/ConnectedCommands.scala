package com.couchmate.services.user.commands

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder}
import com.couchmate.api.ws.protocol.{External, LoginError, LoginErrorCause, Protocol, RegisterAccountError, RegisterAccountErrorCause}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.data.User
import com.couchmate.services.GridCoordinator
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser._
import com.couchmate.services.user.context.{GeoContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{JwtExtension, MailExtension, PromExtension, SingletonExtension, UserExtension}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ConnectedCommands {
  private[user] def disconnect(
    userContext: UserContext,
    geo: GeoContext
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    metrics: PromExtension,
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command]
  ): EffectBuilder[PersistentUser.Disconnected.type, State] = Effect
    .persist(Disconnected)
    .thenRun((_: State) => metrics.decSession(
      userContext.providerId,
      userContext.providerName,
      geo.timezone,
      geo.country
    ))
    .thenRun((_: State) =>
      singletons.gridCoordinator ! GridCoordinator.RemoveListener(
        userContext.providerId, gridAdapter
      )
    )
    .thenRun((_: State) => ctx.log.debug(s"User ${userContext.user.userId.get} disconnected"))
    .thenStop

  private[user] def updateGrid(
    grid: Grid,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
  ) = Effect
    .none
    .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.UpdateGrid(grid)))

  private[user] def validateEmail(email: String)(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    ec: ExecutionContext,
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.validateEmail(email)) {
      case Success(value) => value
      case Failure(exception) => EmailValidationFailed(exception)
    })

  private[user] def emailValidated(
    emailValidated: EmailValidated,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    ec: ExecutionContext
  ) = Effect
    .none
    .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.ValidateEmailResponse(
      emailValidated.exists,
      emailValidated.valid
    )))

  private[user] def validateUsername(username: String)(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    ec: ExecutionContext,
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.validateUsername(username)) {
      case Success(value) => value
      case Failure(exception) => UsernameValidationFailed(exception)
    })

  private[user] def usernameValidated(
    usernameValidated: UsernameValidated,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    ec: ExecutionContext,
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ws ! WSPersistentActor.OutgoingMessage(External.ValidateUsernameResponse(
      usernameValidated.exists,
      usernameValidated.valid
    )))

  private[user] def registerAccount(
    userId: UUID,
    email: String,
    password: String
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    ec: ExecutionContext,
    mail: MailExtension,
    jwt: JwtExtension
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.registerAccount(
      userId, email, password
    )) {
      case Success(Right(_)) => AccountRegistered
      case Success(Left(ex)) => AccountRegistrationFailed(ex)
      case Failure(_) => AccountRegistrationFailed(RegisterAccountError(
        RegisterAccountErrorCause.UnknownError
      ))
    })

  private[user] def verifyAccount(
    userContext: UserContext,
    token: String
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    ec: ExecutionContext,
    jwt: JwtExtension
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.verifyAccount(userContext, token)) {
      case Success(Right(userId)) => AccountVerified(userId)
      case Success(Left(ex)) => AccountVerificationFailed(ex)
      case Failure(_) => AccountVerificationFailed(RegisterAccountError(
        RegisterAccountErrorCause.UnknownError
      ))
    })

  private[user] def accountVerified(userContext: UserContext)(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    user: UserExtension
  ): EffectBuilder[UserContextSet, State] = Effect
    .persist(UserContextSet(
      userContext.copy(
        user = userContext.user.copy(
          verified = true
        )
      )
    ))
    .thenRun {
      case ConnectedState(userContext, _, ws) =>
        ws ! WSPersistentActor.OutgoingMessage(
          External.VerifyAccountSuccess(userContext.getClientUser)
        )
    }

  private[user] def login(
    email: String,
    password: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.login(email, password)) {
      case Success(Right(userId)) => LoggedIn(userId)
      case Success(Left(ex)) => LogInFailed(ex)
      case Failure(_) => LogInFailed(LoginError(
        LoginErrorCause.Unknown
      ))
    })

  private[user] def loggedIn(
    userId: UUID,
    geoContext: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    user: UserExtension
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ws ! WSPersistentActor.SetUser(userId))
    .thenRun((_: State) => ctx.self ! Disconnect)

  private[user] def logout(geo: GeoContext)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): Effect[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.createUser(geo)) {
      case Success(user: User) => LoggedOut(user.userId.get)
      case Failure(exception) => LogoutFailed(exception)
    })

  private[user] def loggedOut(
    userId: UUID,
    geoContext: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    user: UserExtension
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ws ! WSPersistentActor.SetUser(userId))
    .thenRun((_: State) => ctx.self ! Disconnect)
}
