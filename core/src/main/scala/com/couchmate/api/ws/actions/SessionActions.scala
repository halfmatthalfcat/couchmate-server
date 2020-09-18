package com.couchmate.api.ws.actions

import java.time.{Duration, LocalDateTime, ZoneId}

import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.api.ws.Commands.Command
import com.couchmate.api.ws.{DeviceContext, GeoContext, SessionContext}
import com.couchmate.api.ws.protocol.{LoginError, LoginErrorCause, RestoreSession}
import com.couchmate.common.dao.{GridDAO, ProviderDAO, UserActivityDAO, UserDAO, UserMetaDAO, UserMuteDAO, UserPrivateDAO, UserProviderDAO, UserWordBlockDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{UserActivity, UserActivityType, UserMeta, UserMute, UserPrivate, UserProvider, UserRole, User => InternalUser}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteDefaultProvider
import com.couchmate.util.akka.extensions.{CMJwtClaims, JwtExtension}
import com.github.halfmatthalfcat.moniker.Moniker
import com.neovisionaries.i18n.CountryCode

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object SessionActions
  extends UserDAO
  with UserMetaDAO
  with UserPrivateDAO
  with UserMuteDAO
  with UserWordBlockDAO
  with UserActivityDAO
  with UserProviderDAO
  with ProviderDAO
  with GridDAO {

  private[this] val moniker: Moniker = Moniker()

  def resumeSession(
    resume: RestoreSession,
    geo: GeoContext,
    device: DeviceContext
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[Command],
    jwt: JwtExtension
  ): Future[SessionContext] =
    jwt.validateToken(resume.token, Map("scope" -> "access")) match {
      case Failure(exception) =>
        ctx.log.error(s"Got bad token", exception)
        createNewSession(geo, device)
      case Success(CMJwtClaims(userId, _)) => getUser(userId) flatMap {
        case None =>
          ctx.log.warn(s"Couldn't find user $userId")
          createNewSession(geo, device)
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
            userId = userId,
            action = UserActivityType.Login,
            os = device.os,
            osVersion = device.osVersion,
            brand = device.brand,
            model = device.model
          ))
        } yield SessionContext(
          user = user,
          userMeta = userMeta,
          providerId = userProvider.providerId,
          providerName = provider.name,
          token = resume.token,
          mutes = mutes,
          wordMutes = wordMutes,
          airings = Set.empty,
          grid = grid
        ).setAiringsFromGrid(grid)
      }
    }

  def createNewSession(context: GeoContext, device: DeviceContext)(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Future[SessionContext] = {
    val defaultProvider: GracenoteDefaultProvider =
      getDefaultProvider(context)

    for {
      user <- upsertUser(InternalUser(
        userId = None,
        role = UserRole.Anon,
        active = true,
        verified = false,
        created = LocalDateTime.now(ZoneId.of("UTC"))
      ))
      userMeta <- upsertUserMeta(UserMeta(
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
      mutes <- getUserMutes(user.userId.get)
      wordMutes <- getUserWordBlocks(user.userId.get)
      provider <- getProvider(defaultProvider.value)
      token <- Future.fromTry(jwt.createToken(
        subject = user.userId.get.toString,
        claims = Map(
          "scope" -> "access"
        ),
        expiry = Duration.ofDays(365L)
      ))
      _ <- addUserActivity(UserActivity(
        userId = user.userId.get,
        action = UserActivityType.Login,
        os = device.os,
        osVersion = device.osVersion,
        brand = device.brand,
        model = device.model
      ))
      grid <- getGrid(defaultProvider.value)
    } yield SessionContext(
      user = user,
      userMeta = userMeta,
      providerId = provider.get.providerId.get,
      providerName = provider.get.name,
      token = token,
      mutes = mutes,
      wordMutes = wordMutes,
      airings = Set.empty,
      grid = grid
    ).setAiringsFromGrid(grid)
  }

  def login(
    session: SessionContext,
    device: DeviceContext,
    email: String,
    password: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Future[SessionContext] = {
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
      token <- Future.fromTry(jwt.createToken(
        user.userId.get.toString,
        Map("scope" -> "access"),
        Duration.ofDays(30)
      ))
      userMeta <- getUserMeta(user.userId.get)
      mutes <- getUserMutes(user.userId.get)
      wordMutes <- getUserWordBlocks(user.userId.get)
      userProvider <- getUserProvider(user.userId.get)
      provider <- getProvider(userProvider.get.providerId)
      grid <- getGrid(userProvider.get.providerId)
      // Log out the previous (usually anon) session
      _ <- addUserActivity(UserActivity(
        userId = session.user.userId.get,
        action = UserActivityType.Logout,
        os = device.os,
        osVersion = device.osVersion,
        brand = device.brand,
        model = device.model
      ))
      // Log into the new session
      _ <- addUserActivity(UserActivity(
        userId = user.userId.get,
        action = UserActivityType.Login,
        os = device.os,
        osVersion = device.osVersion,
        brand = device.brand,
        model = device.model
      ))
    } yield session.copy(
      user,
      userMeta.get,
      provider.get.providerId.get,
      provider.get.name,
      token,
      mutes,
      wordMutes,
      airings = Set(),
      grid
    ).setAiringsFromGrid(grid)
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
