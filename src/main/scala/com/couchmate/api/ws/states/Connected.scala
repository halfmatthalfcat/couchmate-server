package com.couchmate.api.ws.states

import java.time.Duration

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.api.JwtProvider
import com.couchmate.api.ws.Commands.{Command, Incoming, Outgoing, PartialCommand, Connected => ConnectedCommands}
import com.couchmate.api.ws.{GeoContext, SessionContext, WSClient}
import com.couchmate.api.ws.protocol._
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{ProviderDAO, UserDAO, UserMetaDAO, UserProviderDAO}
import com.couchmate.data.models.{UserMeta, UserProvider, UserRole, User => InternalUser}
import com.couchmate.external.gracenote.models.GracenoteDefaultProvider
import com.couchmate.external.gracenote.provider.ProviderJob
import com.couchmate.services.{ListingCoordinator, ProviderCoordinator}
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}
import com.github.halfmatthalfcat.moniker.Moniker
import com.neovisionaries.i18n.CountryCode

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * A Connected WS Client
 */

class Connected private[ws] (
  ctx: ActorContext[Command],
  ws: ActorRef[Command]
) extends BaseState(ctx, ws) {
  import ConnectedCommands._

  implicit val ec: ExecutionContext = ctx.executionContext
  implicit val db: Database = DatabaseExtension(ctx.system).db
  val providerCoordinator: ActorRef[ProviderCoordinator.Command] =
    SingletonExtension(ctx.system).providerCoordinator
  val listingCoordinator: ActorRef[ListingCoordinator.Command] =
    SingletonExtension(ctx.system).listingCoordinator

  val providerAdapter: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
    case ProviderJob.JobEnded(_, _, providers) => Outgoing(GetProvidersResponse(providers))
  }

  override protected def internal: PartialCommand = {
    case CreateNewSessionSuccess(session, geo) =>
      new InSession(
        session,
        geo,
        ctx,
        ws
      ).run
  }

  override protected def incoming: PartialCommand = {
    case Incoming(InitSession(timezone, locale)) =>
      val geoContext: GeoContext = GeoContext(locale, timezone)
      ctx.pipeToSelf(Connected.createNewSession(geoContext)) {
        case Success(value) => CreateNewSessionSuccess(value, geoContext)
        case Failure(exception) => CreateNewSessionFailure(exception)
       }
      Behaviors.same
  }
}

object Connected
  extends JwtProvider
  with UserDAO
  with UserMetaDAO
  with ProviderDAO
  with UserProviderDAO {

  val moniker: Moniker = Moniker()

  def getDefaultProvider(context: GeoContext): GracenoteDefaultProvider = context match {
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

  def createNewSession(context: GeoContext)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = ({
    val defaultProvider: GracenoteDefaultProvider =
      getDefaultProvider(context)

    for {
      user <- upsertUser(InternalUser(
        userId = None,
        role = UserRole.Anon,
        active = true,
        verified = false
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
      provider <- getProvider(defaultProvider.value)
      token <- Future.fromTry(createToken(
        subject = user.userId.get.toString,
        claims = Map(),
        expiry = Duration.ofDays(365L)
      ))
    } yield SessionContext(
      user = user,
      userMeta = userMeta,
      providerId = provider.get.providerId.get,
      providerName = provider.get.name,
      token = token
    )
  }) recoverWith {
    case ex: Throwable =>
      System.out.println(s"----- ERROR: ${ex.getMessage}")
      Future.failed(ex)
  }
}
