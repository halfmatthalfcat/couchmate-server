package com.couchmate.services.user.commands

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder}
import com.couchmate.api.ws.protocol.{External, ForgotPasswordError, ForgotPasswordErrorCause, LoginError, LoginErrorCause, PasswordResetError, PasswordResetErrorCause, Protocol, RegisterAccountError, RegisterAccountErrorCause, UpdateUsernameError, UpdateUsernameErrorCause}
import com.couchmate.common.dao.UserActivityDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.api.user.UserMute
import com.couchmate.common.models.data.{ApplicationPlatform, User, UserActivity, UserActivityType, UserMeta}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser._
import com.couchmate.services.user.context.{GeoContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{JwtExtension, MailExtension, PromExtension, SingletonExtension, UserExtension}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ConnectedCommands
  extends UserActivityDAO {

  private[user] def disconnect(
    userContext: UserContext,
    geo: GeoContext
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    metrics: PromExtension,
    singletons: SingletonExtension,
    gridAdapter: ActorRef[GridCoordinator.Command],
    db: Database
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
    .thenRun((_: State) => addUserActivity(UserActivity(
      userContext.user.userId.get,
      UserActivityType.Logout,
      os = Option.empty,
      osVersion = Option.empty,
      brand = Option.empty,
      model = Option.empty,
    )))
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
    ws: ActorRef[WSPersistentActor.Command]
  )(
    implicit
    ctx: ActorContext[PersistentUser.Command],
    user: UserExtension
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ws ! WSPersistentActor.SetUser(userId))
    .thenRun((_: State) => ctx.self ! Disconnect)

  private[user] def forgotPassword(email: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    mail: MailExtension,
    jwt: JwtExtension,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.sendForgotPassword(email)) {
      case Success(Right(_)) => ForgotPasswordSent
      case Success(Left(ex)) => ForgotPasswordFailed(ex)
      case Failure(_) => ForgotPasswordFailed(ForgotPasswordError(
        ForgotPasswordErrorCause.Unknown
      ))
    })

  private[user] def forgotPasswordReset(password: String, token: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.forgotPassword(token, password)) {
      case Success(Right(_)) => ForgotPasswordReset
      case Success(Left(ex)) => ForgotPasswordResetFailed(ex)
      case Failure(_) => ForgotPasswordResetFailed(ForgotPasswordError(
        ForgotPasswordErrorCause.Unknown
      ))
    })

  private[user] def resetPassword(
    userId: UUID,
    oldPassword: String,
    newPassword: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.passwordReset(
      userId,
      oldPassword,
      newPassword
    )) {
      case Success(Right(_)) => PasswordReset
      case Success(Left(ex)) => PasswordResetFailed(ex)
      case Failure(_) => PasswordResetFailed(PasswordResetError(
        PasswordResetErrorCause.Unknown
      ))
    })

  private[user] def updateUsername(
    userContext: UserContext,
    username: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun((_: State) => ctx.pipeToSelf(UserActions.updateUsername(
      userContext, username
    )) {
      case Success(Right(userMeta)) => UpdateUsername(userMeta)
      case Success(Left(ex)) => UpdateUsernameFailed(ex)
      case Failure(_) => UpdateUsernameFailed(UpdateUsernameError(
        UpdateUsernameErrorCause.Unknown
      ))
    })

  private[user] def usernameUpdated(userMeta: UserMeta): Effect[UsernameUpdated, State] =
    Effect
      .persist(UsernameUpdated(userMeta))
      .thenRun({
        case ConnectedState(userContext, geo, ws) => ws ! WSPersistentActor.OutgoingMessage(
          External.UpdateUsernameSuccess(userContext.getClientUser)
        )
      })

  private[user] def muteParticipant(userContext: UserContext, userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun(_ => ctx.pipeToSelf(UserActions.muteParticipant(userContext, userId)) {
      case Success(Right(mutes)) => MuteParticipant(mutes)
      case Success(Left(ex)) => MuteParticipantFailed(ex)
      case Failure(exception) => MuteParticipantFailed(exception)
    })

  private[user] def participantMuted(mutes: Seq[UserMute]): Effect[ParticipantMuted, State] =
    Effect
      .persist(ParticipantMuted(mutes))
      .thenRun({
        case ConnectedState(userContext, geo, ws) => ws ! WSPersistentActor.OutgoingMessage(
          External.UpdateMutes(mutes)
        )
      })

  private[user] def unmuteParticipant(userContext: UserContext, userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun(_ => ctx.pipeToSelf(UserActions.unmuteParticipant(userContext, userId)) {
      case Success(Right(mutes)) => UnmuteParticipant(mutes)
      case Success(Left(ex)) => UnmuteParticipantFailed(ex)
      case Failure(exception) => UnmuteParticipantFailed(exception)
    })

  private[user] def participantUnmuted(mutes: Seq[UserMute]): Effect[ParticipantUnmuted, State] =
    Effect
      .persist(ParticipantUnmuted(mutes))
      .thenRun({
        case ConnectedState(userContext, geo, ws) => ws ! WSPersistentActor.OutgoingMessage(
          External.UpdateMutes(userContext.mutes)
        )
      })

  private[user] def muteWord(userId: UUID, word: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun(_ => ctx.pipeToSelf(UserActions.muteWord(userId, word)) {
      case Success(Right(value)) => MuteWord(value)
      case Success(Left(ex)) => MuteWordFailed(ex)
      case Failure(exception) => MuteWordFailed(exception)
    })

  private[user] def wordMuted(mutes: Seq[String]): Effect[WordMuted, State] =
    Effect
      .persist(WordMuted(mutes))
      .thenRun({
        case ConnectedState(userContext, geo, ws) => ws ! WSPersistentActor.OutgoingMessage(
          External.UpdateWordMutes(userContext.wordMutes)
        )
      })

  private[user] def unmuteWord(userId: UUID, word: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun(_ => ctx.pipeToSelf(UserActions.unmuteWord(userId, word)) {
      case Success(Right(value)) => UnmuteWord(value)
      case Success(Left(ex)) => UnmuteWordFailed(ex)
      case Failure(exception) => UnmuteWordFailed(exception)
    })

  private[user] def wordUnmuted(mutes: Seq[String]): Effect[WordUnmuted, State] =
    Effect
      .persist(WordUnmuted(mutes))
      .thenRun({
        case ConnectedState(userContext, geo, ws) => ws ! WSPersistentActor.OutgoingMessage(
          External.UpdateWordMutes(userContext.wordMutes)
        )
      })

  private[user] def enableNotifications(
    userId: UUID,
    os: ApplicationPlatform,
    token: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun(_ => ctx.pipeToSelf(UserActions.enableNotifications(userId, os, token)) {
      case Success(_) => EnabledNotifications
      case Failure(exception) => EnableNotificationsFailed(exception)
    })

  private[user] def disableNotifications(userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command]
  ): EffectBuilder[Nothing, State] = Effect
    .none
    .thenRun(_ => ctx.pipeToSelf(UserActions.disableNotifications(userId)) {
      case Success(_) => DisabledNotifications
      case Failure(exception) => DisableNotificationsFailed(exception)
    })
}
