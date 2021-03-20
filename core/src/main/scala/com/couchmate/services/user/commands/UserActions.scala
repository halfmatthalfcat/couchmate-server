package com.couchmate.services.user.commands

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID
import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.Server
import com.couchmate.api.ws.protocol.External.ReportParticipant
import com.couchmate.api.ws.protocol.RegisterAccountErrorCause.EmailExists
import com.couchmate.api.ws.protocol._
import com.couchmate.common.dao._
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{UserMute, User => InternalUser, _}
import com.couchmate.common.models.api.user.{UserMute => ExternalUserMute}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteDefaultProvider
import com.couchmate.services.user.PersistentUser
import com.couchmate.services.user.PersistentUser.{EmailValidated, UserNotificationAdded, UserNotificationOnlyNewChanged, UserNotificationRemoved, UserNotificationToggled, UsernameValidated}
import com.couchmate.services.user.context.{DeviceContext, GeoContext, UserContext}
import com.couchmate.util.akka.extensions.{JwtExtension, MailExtension}
import com.couchmate.util.http.HttpActor
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
  with UserNotificationConfigurationDAO
  with UserNotificationShowDAO
  with UserNotificationSeriesDAO
  with UserNotificationTeamDAO
  with UserNotificationQueueDAO
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
          .mkString(""),
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
    geo: GeoContext
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[HttpActor.Command],
    jwt: JwtExtension
  ): Future[UUID] = (for {
    token <- maybeToken
    claims <- jwt.validateToken(
      token,
      Map("scope" -> "access")
    ).toOption
  } yield claims.userId) match {
    case None => for {
      userId <- createUser(geo).map(_.userId.get)
    } yield userId
    case Some(userId) => for {
      exists <- getUser(userId)
      userId <- exists.fold(
        createUser(geo).map(_.userId.get)
      )(user => Future.successful(user.userId.get))
    } yield userId
  }

  private[user] def createUserContext(userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[PersistentUser.Command],
    jwt: JwtExtension,
  ): Future[UserContext] = (getUser(userId) flatMap {
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
      token <- Future.fromTry(jwt.createToken(
        subject = user.userId.get.toString,
        claims = Map(
          "scope" -> "access"
        ),
        expiry = Duration.ofDays(365L)
      ))
      notifications <- getUserNotifications(userId)
      notificationConfigurations <- getUserNotificationConfigurations(userId)
    } yield UserContext(
      user = user,
      userMeta = userMeta,
      providerId = userProvider.providerId,
      providerName = provider.name,
      token = token,
      mutes = mutes,
      wordMutes = wordMutes,
      notifications = notifications,
      notificationConfigurations = notificationConfigurations
    )
  }) recoverWith {
    case ex: Throwable =>
      ctx.log.error(s"Error creating user context", ex)
      Future.failed(ex)
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
    // This basically assumes that the (anon) user who requested to register is going to be
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
  ): Future[Either[ForgotPasswordError, Unit]] = (for {
    user <- getUserByEmail(email)
    token <- user.fold[Future[String]](
      Future.failed(ForgotPasswordError(ForgotPasswordErrorCause.NoAccountExists))
    )(user => Future.fromTry(jwt.createToken(
      user.userId.get.toString,
      Map("scope" -> "forgot"),
      Duration.ofMinutes(20),
    )))
    _ <- mail.forgotPassword(email, token)
  } yield Right()) recoverWith {
    case error: ForgotPasswordError => Future.successful(Left(error))
    case _: Throwable => Future.successful(Left(ForgotPasswordError(
      ForgotPasswordErrorCause.Unknown
    )))
  }

  private[commands] def forgotPassword(token: String, password: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Future[Either[ForgotPasswordError, Unit]] = ({
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
    } yield Right()
  }) recoverWith {
    case err: ForgotPasswordError => Future.successful(Left(err))
    case _: Throwable => Future.successful(Left(ForgotPasswordError(
      ForgotPasswordErrorCause.Unknown
    )))
  }

  private[commands] def passwordReset(
    userId: UUID,
    oldPassword: String,
    newPassword: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Either[PasswordResetError, Unit]] = ({
    import com.github.t3hnar.bcrypt._

    for {
      userPrivate <- getUserPrivate(userId)
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
        userId,
        hashedPw
      ))
    } yield Right()
  }) recoverWith {
    case ex: PasswordResetError => Future.successful(Left(ex))
    case _: Throwable => Future.successful(Left(PasswordResetError(
      PasswordResetErrorCause.Unknown
    )))
  }

  private[commands] def updateUsername(
    userContext: UserContext,
    username: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Either[UpdateUsernameError, UserMeta]] = {
    val usernameRegex = "^[a-zA-Z0-9]{1,16}$".r
    if (usernameRegex.matches(username) && userContext.user.verified) {
      usernameExists(username) flatMap {
        case true => Future.successful(Left(UpdateUsernameError(
          UpdateUsernameErrorCause.UsernameExists
        )))
        case false => upsertUserMeta(userContext.userMeta.copy(
          username = username
        )).map(Right.apply)
      }
    } else if (!usernameRegex.matches(username)) {
      Future.successful(Left(UpdateUsernameError(
        UpdateUsernameErrorCause.InvalidUsername
      )))
    } else {
      Future.successful(Left(UpdateUsernameError(
        UpdateUsernameErrorCause.AccountNotRegistered
      )))
    }
  }

  private[commands] def muteParticipant(
    userContext: UserContext,
    userId: UUID
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Either[Throwable, Seq[ExternalUserMute]]] = {
    if (userContext.mutes.exists(_.userId == userId)) {
      Future.successful(Right(userContext.mutes))
    } else {
      (for {
        _ <- addUserMute(UserMute(
          userContext.user.userId.get,
          userId
        ))
        mutes <- getUserMutes(userContext.user.userId.get)
      } yield Right(mutes)) recoverWith {
        case ex: Throwable =>
          Future.successful(Left(ex))
      }
    }
  }

  private[commands] def unmuteParticipant(
    userContext: UserContext,
    userMuteId: UUID
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Either[Throwable, Seq[ExternalUserMute]]] = {
    if (!userContext.mutes.exists(_.userId == userMuteId)) {
      Future.successful(Right(userContext.mutes))
    } else {
      (for {
        _ <- removeUserMute(UserMute(
          userContext.user.userId.get,
          userMuteId
        ))
        mutes <- getUserMutes(userContext.user.userId.get)
      } yield Right(mutes)) recoverWith {
        case ex: Throwable => Future.successful(Left(ex))
      }
    }
  }

  private[commands] def reportParticipant(
    userContext: UserContext,
    report: ReportParticipant
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserReport] = addUserReport(UserReport(
    reportId = None,
    created = Some(LocalDateTime.now(ZoneId.of("UTC"))),
    reporterId = userContext.user.userId.get,
    reporteeId = report.userId,
    reportType = report.userReportType,
    message = report.message
  ))

  private[commands] def muteWord(
    userId: UUID,
    word: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Either[Throwable, Seq[String]]] = (for {
    _ <- addUserWordBlock(UserWordBlock(
      userId = userId,
      word = word
    ))
    list <- getUserWordBlocks(userId)
  } yield Right(list)) recoverWith {
    case ex: Throwable => Future.successful(Left(ex))
  }

  private[commands] def unmuteWord(
    userId: UUID,
    word: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Either[Throwable, Seq[String]]] = (for {
    _ <- removeUserWordBlock(UserWordBlock(
      userId = userId,
      word = word
    ))
    list <- getUserWordBlocks(userId)
  } yield Right(list)) recoverWith {
    case ex: Throwable => Future.successful(Left(ex))
  }

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

  private[commands] def setNotificationToken(
    userId: UUID,
    os: ApplicationPlatform,
    deviceId: Option[String],
    token: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
  ): Future[Seq[UserNotificationConfiguration]] = for {
    _ <- updateUserNotificationToken(
      userId, os, deviceId, token
    )
    notifications <- getUserNotificationConfigurations(userId)
  } yield notifications

  private[commands] def enableNotifications(
    userId: UUID,
    os: ApplicationPlatform,
    deviceId: Option[String]
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
  ): Future[Seq[UserNotificationConfiguration]] = for {
    _ <- updateUserNotificationActive(
      userId, os, deviceId, active = true
    )
    notifications <- getUserNotificationConfigurations(userId)
  } yield notifications

  private[commands] def disableNotifications(
    userId: UUID,
    os: ApplicationPlatform,
    deviceId: Option[String]
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationConfiguration]] = for {
    _ <- updateUserNotificationActive(
      userId, os, deviceId, active = false
    )
    notifications <- getUserNotificationConfigurations(userId)
  } yield notifications

  private[commands] def notificationRead(notificationId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = updateNotificationRead(notificationId)

  private[commands] def addShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    notification <- addOrGetUserShowNotification(
      userId, airingId, providerChannelId, hash
    )
    _ <- addUserShowNotification(
      userId,
      airingId,
      providerChannelId,
      notification.map(_.hash).getOrElse(hash)
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def removeShowNotification(
    userId: UUID,
    airingId: String,
    channelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- removeUserShowNotification(userId, airingId, channelId)
    _ <- removeUserNotificationForShow(userId, airingId)
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def toggleTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- toggleUserTeamNotification(
      userId, teamId, providerId, hash, enabled
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def toggleOnlyNewTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- toggleOnlyNewUserTeamNotification(
      userId, teamId, providerId, hash, onlyNew
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def updateHashTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- updateHashTeamNotification(
      userId, teamId, providerId, hash
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def addTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationAdded] = for {
    notification <- addOrGetUserTeamNotification(
      userId, teamId, providerId, hash
    )
    _ <- addUserNotificationForSportTeam(
      userId,
      teamId,
      providerId,
      notification.map(_.hash).getOrElse(hash),
      notification.forall(_.onlyNew)
    )
    notifications <- getUserNotifications(userId)
  } yield UserNotificationAdded(notifications)

  private[commands] def removeTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationRemoved] = for {
    _ <- removeUserTeamNotification(userId, teamId, providerId)
    _ <- removeUserNotificationForTeam(userId, teamId, providerId)
    notifications <- getUserNotifications(userId)
  } yield UserNotificationRemoved(notifications)

  private[commands] def addSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationAdded] = for {
    notification <- addOrGetUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    )
    _ <- addUserNotificationForSeries(
      userId,
      seriesId,
      providerChannelId,
      notification.map(_.hash).getOrElse(hash),
      notification.forall(_.onlyNew)
    )
    notifications <- getUserNotifications(userId)
  } yield UserNotificationAdded(notifications)

  private[commands] def toggleSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- toggleUserSeriesNotification(
      userId,
      seriesId,
      providerChannelId,
      hash,
      enabled
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def updateHashSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- updateHashUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def toggleOnlyNewSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    _ <- toggleOnlyNewUserSeriesNotification(
      userId, seriesId, providerChannelId, hash, enabled
    )
    notifications <- getUserNotifications(userId)
  } yield notifications

  private[commands] def removeSeriesNotification(
    userId: UUID,
    seriesId: Long,
    channelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationRemoved] = for {
    _ <- removeUserSeriesNotification(userId, seriesId, channelId)
    _ <- removeUserNotificationForSeries(userId, seriesId, channelId)
    notifications <- getUserNotifications(userId)
  } yield UserNotificationRemoved(notifications)

  private[this] def getUserNotifications(userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotifications] = for {
    shows <- getUserShowNotifications(userId)
    series <- getUserSeriesNotifications(userId)
    teams <- getUserTeamNotifications(userId)
  } yield UserNotifications(
    shows,
    series,
    teams
  )

  private[commands] def updateProvider(
    userId: UUID,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Provider] = for {
    _ <- updateUserProvider(UserProvider(
      userId = userId,
      providerId = providerId
    ))
    provider <- getProvider(providerId)
  } yield provider.get

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
