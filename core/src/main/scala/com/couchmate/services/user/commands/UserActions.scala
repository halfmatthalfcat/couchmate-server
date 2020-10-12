package com.couchmate.services.user.commands

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.Server
import com.couchmate.api.ws.SessionContext
import com.couchmate.api.ws.protocol.External.ReportParticipant
import com.couchmate.api.ws.protocol.RegisterAccountErrorCause.EmailExists
import com.couchmate.api.ws.protocol._
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.dao._
import com.couchmate.common.models.data.{User => InternalUser, _}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteDefaultProvider
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{EmailValidated, UsernameValidated}
import com.couchmate.services.user.context.{GeoContext, UserContext}
import com.couchmate.util.akka.extensions.{JwtExtension, MailExtension}
import com.couchmate.util.jwt.Jwt.ExpiredJwtError
import com.github.halfmatthalfcat.moniker.Moniker
import com.neovisionaries.i18n.CountryCode

import scala.concurrent.{ExecutionContext, Future}

object UserActions
  extends UserDAO
  with UserMetaDAO
  with UserPrivateDAO
  with UserMuteDAO
  with UserWordBlockDAO
  with UserActivityDAO
  with UserProviderDAO
  with UserReportDAO
  with ProviderDAO
  with GridDAO {

  private[this] val moniker: Moniker = Moniker()

  private[commands] def createUser(geo: GeoContext)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[InternalUser] = {
    val defaultProvider: GracenoteDefaultProvider =
      getDefaultProvider(geo)
    for {
      user <- upsertUser(InternalUser(
        userId = None,
        role = UserRole.Anon,
        active = true,
        verified = false,
        created = LocalDateTime.now(ZoneId.of("UTC"))
      ))
      _ <- upsertUserMeta(UserMeta(
        userId = user.userId.get,
        username = moniker
          .getRandom()
          .split(" ")
          .map(_.capitalize)
          .mkString(" "),
        email = None
      ))
      _ <- addUserProvider(UserProvider(
        userId = user.userId.get,
        defaultProvider.value
      ))
    } yield user
  }

  def getOrCreateUser(
    maybeToken: Option[String],
    geo: GeoContext,
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[Server.Command],
    jwt: JwtExtension
  ): Future[UUID] = (for {
    token <- maybeToken
    claims <- jwt.validateToken(
      token,
      Map("scope" -> "access")
    ).toOption
  } yield claims.userId) match {
    case None => createUser(geo).map(_.userId.get)
    case Some(userId) => Future.successful(userId)
  }

  private[user] def createUserContext(userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command],
    jwt: JwtExtension,
  ): Future[UserContext] = getUser(userId) flatMap {
    case None =>
      ctx.log.warn(s"Couldn't find user $userId")
      Future.failed(new RuntimeException(s"Couldn't find user $userId"))
    case Some(user @ InternalUser(Some(userId), _, _, _, _)) => for {
      userMeta <- getUserMeta(userId).map(_.getOrElse(
        throw new RuntimeException(s"Could not find userMeta for $userId")
      ))
      mutes <- getUserMutes(userId)
      wordMutes <- getUserWordBlocks(userId)
      userProvider <- getUserProvider(userId).map(_.getOrElse(
        throw new RuntimeException(s"Could not find userProvider for $userId")
      ))
      provider <- getProvider(userProvider.providerId).map(_.getOrElse(
        throw new RuntimeException(s"Could not find provider for $userId (${userProvider.providerId})")
      ))
      grid <- getGrid(userProvider.providerId)
      _ <- addUserActivity(UserActivity(
        userId = user.userId.get,
        action = UserActivityType.Login,
        os = Option.empty,
        osVersion = Option.empty,
        brand = Option.empty,
        model = Option.empty,
      ))
      token <- Future.fromTry(jwt.createToken(
        subject = user.userId.get.toString,
        claims = Map(
          "scope" -> "access"
        ),
        expiry = Duration.ofDays(365L)
      ))
    } yield UserContext(
      user = user,
      userMeta = userMeta,
      providerId = userProvider.providerId,
      providerName = provider.name,
      token = token,
      mutes = mutes,
      wordMutes = wordMutes
    )
  }

  private[commands] def registerAccount(
    userId: UUID,
    email: String,
    password: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command],
    mail: MailExtension,
    jwt: JwtExtension
  ): Future[Either[RegisterAccountError, Unit]] = {
    import com.github.t3hnar.bcrypt._

    (for {
      _ <- emailExists(email) flatMap {
        case true => Future.failed(RegisterAccountError(EmailExists))
        case false => Future.successful()
      }
      hashed <- Future.fromTry(password.bcryptSafe(10))
      token <- Future.fromTry(jwt.createToken(
        userId.toString,
        Map(
          "scope" -> "register",
          "email" -> email.toLowerCase,
          "password" -> hashed
        ),
        Duration.ofMinutes(20)
      ))
      _ <- mail.accountRegistration(
        email,
        token
      )
    } yield Right()) recoverWith {
      case err: RegisterAccountError =>
        Future.successful(Left(err))
      case ex: Throwable =>
        ctx.log.error("Failed to register account", ex)
        Future.successful(Left(RegisterAccountError(RegisterAccountErrorCause.UnknownError)))
    }
  }

  private[commands] def verifyAccount(
    userContext: UserContext,
    token: String
  )(
    implicit
    ec: ExecutionContext,
    ctx: ActorContext[PersistentUser.Command],
    db: Database,
    jwt: JwtExtension
  ): Future[Either[RegisterAccountError, UUID]] = (for {
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
    _ <- if (userId != userContext.user.userId.get) {
      Future.failed(RegisterAccountError(RegisterAccountErrorCause.UserMismatch))
    } else { Future.successful() }
    _ <- upsertUser(userContext.user.copy(
      role = UserRole.Registered,
      verified = true
    ))
    _ <- upsertUserMeta(userContext.userMeta.copy(
      email = Some(email)
    ))
    _ <- upsertUserPrivate(UserPrivate(
      userId = userContext.user.userId.get,
      password = hashedPw
    ))
  } yield Right(userId)) recoverWith {
    case err: RegisterAccountError =>
      Future.successful(Left(err))
    case ex: Throwable =>
      ctx.log.error(s"Failed to validate account", ex)
      Future.successful(Left(RegisterAccountError(
        RegisterAccountErrorCause.UnknownError
      )))
  }

  private[commands] def login(
    email: String,
    password: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
  ): Future[Either[LoginError, UUID]] = {
    import com.github.t3hnar.bcrypt._

    for {
      userExists <- getUserByEmail(email.toLowerCase)
      userPrivate <- userExists.fold[Future[Option[UserPrivate]]](
        Future.failed(LoginError(LoginErrorCause.BadCredentials))
      ) { user =>
        if (!user.verified) {
          Future.failed(LoginError(LoginErrorCause.NotVerified))
        } else {
          getUserPrivate(user.userId.get)
        }
      }
      valid <- userPrivate.fold[Future[Boolean]](
        Future.failed(LoginError(LoginErrorCause.Unknown))
      )(userP => Future.fromTry(password.isBcryptedSafe(userP.password)))
      _ <- if (!valid) {
        Future.failed(LoginError(LoginErrorCause.BadCredentials))
      } else { Future.successful() }
      user = userExists.get
    } yield Right(user.userId.get)
  } recoverWith {
    case err: LoginError =>
      Future.successful(Left(err))
    case ex: Throwable =>
      Future.successful(Left(LoginError(LoginErrorCause.Unknown)))
  }

  private[commands] def sendForgotPassword(email: String)(
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

  private[commands] def forgotPassword(token: String, password: String)(
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

  private[commands] def passwordReset(
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

  private[commands] def updateUsername(
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

  private[commands] def muteParticipant(
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

  private[commands] def unmuteParticipant(
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

  private[commands] def reportParticipant(
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

  private[commands] def addWordBlock(
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

  private[commands] def removeWordBlock(
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

  private[commands] def validateEmail(email: String)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[EmailValidated] = {
    val emailRegex = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])".r
    if (emailRegex.matches(email)) {
      UserActions.emailExists(email) map { exists =>
        EmailValidated(
          exists = exists,
          valid = true
        )
      }
    } else {
      Future.successful(EmailValidated(
        exists = false,
        valid = false
      ))
    }
  }

  private[commands] def validateUsername(username: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
  ): Future[UsernameValidated] = {
    val usernameRegex = "^[a-zA-Z0-9]{1,16}$".r
    if (usernameRegex.matches(username)) {
      UserActions.usernameExists(username) map { exists =>
        UsernameValidated(
          exists = exists,
          valid = true
        )
      }
    } else {
      Future.successful(UsernameValidated(
        exists = false,
        valid = false
      ))
    }
  }

  private[this] def getDefaultProvider(context: GeoContext): GracenoteDefaultProvider = context match {
    case GeoContext("EST" | "EDT", Some(CountryCode.US)) =>
      GracenoteDefaultProvider.USEast
    case GeoContext("EST" | "EDT", Some(CountryCode.CA)) =>
      GracenoteDefaultProvider.CANEast
    case GeoContext("CST" | "CDT", Some(CountryCode.US)) =>
      GracenoteDefaultProvider.USCentral
    case GeoContext("CST" | "CDT", Some(CountryCode.CA)) =>
      GracenoteDefaultProvider.CANCentral
    case GeoContext("MST" | "MDT", Some(CountryCode.US)) =>
      GracenoteDefaultProvider.USMountain
    case GeoContext("MST" | "MDT", Some(CountryCode.CA)) =>
      GracenoteDefaultProvider.CANMountain
    case GeoContext("PST" | "PDT", Some(CountryCode.US)) =>
      GracenoteDefaultProvider.USPacific
    case GeoContext("PST" | "PDT", Some(CountryCode.CA)) =>
      GracenoteDefaultProvider.CANPacific
    case GeoContext("HST" | "HDT", Some(CountryCode.US)) =>
      GracenoteDefaultProvider.USHawaii
    case GeoContext("AST" | "ADT", Some(CountryCode.US)) =>
      GracenoteDefaultProvider.USAlaska
    case _ => GracenoteDefaultProvider.USEast
  }
}
