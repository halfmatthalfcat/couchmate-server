package com.couchmate.api.ws.actions

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.api.ws.Commands.Command
import com.couchmate.api.ws.protocol.RegisterAccountErrorCause.EmailExists
import com.couchmate.api.ws.protocol.External._
import com.couchmate.api.ws.protocol.{ForgotPasswordError, ForgotPasswordErrorCause, PasswordResetError, PasswordResetErrorCause, RegisterAccountError, RegisterAccountErrorCause, UpdateUsernameError, UpdateUsernameErrorCause}
import com.couchmate.api.ws.{DeviceContext, SessionContext}
import com.couchmate.common.dao._
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data._
import com.couchmate.util.akka.extensions.{JwtExtension, MailExtension}
import com.couchmate.util.jwt.Jwt.ExpiredJwtError

import scala.concurrent.{ExecutionContext, Future}

object UserActions
  extends UserDAO
  with UserMetaDAO
  with UserProviderDAO
  with UserPrivateDAO
  with UserMuteDAO
  with UserActivityDAO
  with UserReportDAO
  with UserWordBlockDAO {

  def registerAccount(session: SessionContext, register: RegisterAccount)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[Command],
    mail: MailExtension,
    jwt: JwtExtension
  ): Future[Unit] = {
    import com.github.t3hnar.bcrypt._

    (for {
      _ <- emailExists(register.email) flatMap {
        case true => Future.failed(RegisterAccountError(EmailExists))
        case false => Future.successful()
      }
      hashed <- Future.fromTry(register.password.bcryptSafe(10))
      token <- Future.fromTry(jwt.createToken(
        session.user.userId.get.toString,
        Map(
          "scope" -> "register",
          "email" -> register.email.toLowerCase,
          "password" -> hashed
        ),
        Duration.ofMinutes(20)
      ))
      _ <- mail.accountRegistration(
        register.email,
        token
      )
    } yield ()) recoverWith {
      case err: RegisterAccountError =>
        throw err
      case ex: Throwable =>
        ctx.log.error("Failed to register account", ex)
        Future.failed(RegisterAccountError(RegisterAccountErrorCause.UnknownError))
    }
  }

  def verifyAccount(session: SessionContext, device: DeviceContext, token: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Future[SessionContext] = for {
    claims <- Future.fromTry(jwt.validateToken(
      token,
      Map("scope" -> "register")
    )) recoverWith {
      case ExpiredJwtError => Future.failed(RegisterAccountError(
        RegisterAccountErrorCause.TokenExpired
      ))
      case _ => Future.failed(RegisterAccountError(
        RegisterAccountErrorCause.BadToken
      ))
    }
    userId = claims.userId
    email = claims.claims.getStringClaim("email")
    hashedPw = claims.claims.getStringClaim("password")
    // TODO: this _could_ cause issues in the future but for now it's a safety feature
    // This basically assumes that the (anon) user who requested to regiser is going to be
    // The same one who is ultimately registering.
    // This could _not_ be the case if the user registers on a different device (web)
    // Where the userId is different and would hit this mismatch
    _ <- if (userId != session.user.userId.get) {
      Future.failed(RegisterAccountError(RegisterAccountErrorCause.UserMismatch))
    } else { Future.successful() }
    user <- upsertUser(session.user.copy(
      role = UserRole.Registered,
      verified = true
    ))
    meta <- upsertUserMeta(session.userMeta.copy(
      email = Some(email)
    ))
    _ <- upsertUserPrivate(UserPrivate(
      userId = session.user.userId.get,
      password = hashedPw
    ))
    _ <- addUserActivity(UserActivity(
      userId = session.user.userId.get,
      action = UserActivityType.Registered,
      os = device.os,
      osVersion = device.osVersion,
      brand = device.brand,
      model = device.model
    ))
  } yield session.copy(
    user = user,
    userMeta = meta
  )

  def sendForgotPassword(email: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    mail: MailExtension,
    jwt: JwtExtension
  ): Future[Unit] = for {
    user <- getUserByEmail(email)
    token <- user.fold[Future[String]](
      Future.failed(ForgotPasswordError(ForgotPasswordErrorCause.NoAccountExists))
    )(user => Future.fromTry(jwt.createToken(
      user.userId.get.toString,
      Map("scope" -> "forgot"),
      Duration.ofMinutes(20),
    )))
    _ <- mail.forgotPassword(email, token)
  } yield ()

  def forgotPassword(token: String, password: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Future[Unit] = {
    import com.github.t3hnar.bcrypt._

    for {
      claims <- Future.fromTry(jwt.validateToken(
        token,
        Map("scope" -> "forgot")
      )) recoverWith {
        case ExpiredJwtError => Future.failed(ForgotPasswordError(
          ForgotPasswordErrorCause.TokenExpired
        ))
        case _ => Future.failed(ForgotPasswordError(
          ForgotPasswordErrorCause.BadToken
        ))
      }
      hashedPw <- Future.fromTry(password.bcryptSafe(10))
      _ <- upsertUserPrivate(UserPrivate(
        claims.userId,
        hashedPw
      ))
    } yield ()
  }

  def passwordReset(
    session: SessionContext,
    oldPassword: String,
    newPassword: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Unit] = {
    import com.github.t3hnar.bcrypt._

    for {
      userPrivate <- getUserPrivate(session.user.userId.get)
      valid <- userPrivate.fold[Future[Boolean]](
        Future.failed(PasswordResetError(
          PasswordResetErrorCause.Unknown
        )))(uP => Future.fromTry(oldPassword.isBcryptedSafe(uP.password)))
      _ <- if (!valid) {
        Future.failed(PasswordResetError(
          PasswordResetErrorCause.BadPassword
        ))
      } else { Future.successful() }
      hashedPw <- Future.fromTry(newPassword.bcryptSafe(10))
      _ <- upsertUserPrivate(UserPrivate(
        session.user.userId.get,
        hashedPw
      ))
    } yield ()
  }

  def updateUsername(
    session: SessionContext,
    username: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = {
    val usernameRegex = "^[a-zA-Z0-9]{1,16}$".r
    if (usernameRegex.matches(username)) {
      usernameExists(username) flatMap {
        case true => Future.failed(UpdateUsernameError(
          UpdateUsernameErrorCause.UsernameExists
        ))
        case false => upsertUserMeta(session.userMeta.copy(
          username = username
        )) map(uM => session.copy(userMeta = uM))
      }
    } else {
      Future.failed(UpdateUsernameError(
        UpdateUsernameErrorCause.InvalidUsername
      ))
    }
  }

  def muteParticipant(
    session: SessionContext,
    userId: UUID
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = {
    if (session.mutes.exists(_.userId == userId)) {
      Future.successful(session)
    } else {
      for {
        _ <- addUserMute(UserMute(
          session.user.userId.get,
          userId
        ))
        mutes <- getUserMutes(session.user.userId.get)
      } yield session.copy(
        mutes = mutes
      )
    }
  }

  def unmuteParticipant(
    session: SessionContext,
    userMuteId: UUID
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = {
    if (!session.mutes.exists(_.userId == userMuteId)) {
      Future.successful(session)
    } else {
      for {
        _ <- removeUserMute(UserMute(
          session.user.userId.get,
          userMuteId
        ))
        mutes <- getUserMutes(session.user.userId.get)
      } yield session.copy(
        mutes = mutes
      )
    }
  }

  def reportParticipant(
    session: SessionContext,
    report: ReportParticipant
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserReport] = addUserReport(UserReport(
    reportId = None,
    created = Some(LocalDateTime.now(ZoneId.of("UTC"))),
    reporterId = session.user.userId.get,
    reporteeId = report.userId,
    reportType = report.userReportType,
    message = report.message
  ))

  def addWordBlock(
    session: SessionContext,
    word: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = for {
    _ <- addUserWordBlock(UserWordBlock(
      userId = session.user.userId.get,
      word = word
    ))
    list <- getUserWordBlocks(session.user.userId.get)
  } yield session.copy(
    wordMutes = list
  )

  def removeWordBlock(
    session: SessionContext,
    word: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = for {
    _ <- removeUserWordBlock(UserWordBlock(
      userId = session.user.userId.get,
      word = word
    ))
    list <- getUserWordBlocks(session.user.userId.get)
  } yield session.copy(
    wordMutes = list
  )
}
