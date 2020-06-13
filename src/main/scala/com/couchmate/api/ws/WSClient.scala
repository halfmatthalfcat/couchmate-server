package com.couchmate.api.ws

import java.time.Duration
import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.Server
import com.couchmate.api.JwtProvider
import com.couchmate.api.models.User
import com.couchmate.api.models.room.Participant
import com.couchmate.api.ws.Commands.Connected.{CreateNewSessionFailure, CreateNewSessionSuccess}
import com.couchmate.api.ws.protocol.{AddParticipant, GetProvidersResponse, InitSession, JoinRoom, RemoveParticipant, RoomJoined, SetParticipants, SetSession, UpdateGrid}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao._
import com.couchmate.data.models.{UserMeta, UserProvider, UserRole, User => InternalUser}
import com.couchmate.external.gracenote.models.GracenoteDefaultProvider
import com.couchmate.external.gracenote.provider.ProviderJob
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.Chatroom
import com.couchmate.util.akka.AkkaUtils
import com.couchmate.util.akka.extensions.{DatabaseExtension, PromExtension, RoomExtension, SingletonExtension}
import com.github.halfmatthalfcat.moniker.Moniker
import com.neovisionaries.i18n.CountryCode

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object WSClient
  extends AkkaUtils
  with JwtProvider
  with UserDAO
  with UserMetaDAO
  with ProviderDAO
  with UserProviderDAO
  with UserMuteDAO {
  import Commands._

  val moniker: Moniker = Moniker()

  def apply()(
    implicit
    ec: ExecutionContext,
    context: ActorContext[Server.Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    val metrics: PromExtension =
      PromExtension(ctx.system)
    val lobby: RoomExtension =
      RoomExtension(ctx.system)
    val singletons: SingletonExtension =
      SingletonExtension(ctx.system)

    val providerAdapter: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
      case ProviderJob.JobEnded(_, _, providers) => Outgoing(GetProvidersResponse(providers))
    }

    val gridAdapter: ActorRef[GridCoordinator.Command] = ctx.messageAdapter {
      case GridUpdate(grid) => Outgoing(UpdateGrid(grid))
    }

    val lobbyAdapter: ActorRef[Chatroom.Command] = ctx.messageAdapter {
      case Chatroom.RoomJoined(airingId, roomId) => InRoom.RoomJoined(airingId, roomId)
      case Chatroom.RoomParticipants(participants) => InRoom.SetParticipants(participants)
      case Chatroom.ParticipantJoined(participant) => InRoom.AddParticipant(participant)
      case Chatroom.ParticipantLeft(participant) => InRoom.RemoveParticipant(participant)
    }

    def closing: PartialCommand = {
      case Complete =>
        ctx.log.debug("Connection complete")
        Behaviors.stopped
      case Closed =>
        ctx.log.debug("Connection closed")
        Behaviors.stopped
      case ConnFailure(ex) =>
        ctx.log.error("Connection failed", ex)
        Behaviors.stopped
      case Failed(ex) =>
        ctx.log.error("WSActor failed", ex)
        Behaviors.stopped
    }

    def outgoing(ws: ActorRef[Command]): PartialCommand = {
      case outgoing: Outgoing =>
        ws ! outgoing
        Behaviors.same
    };

    /**
     * Initial Client State
     */
    def run(): Behavior[Command] = Behaviors.receiveMessage(compose(
      {
        case SocketConnected(ws) =>
          connected(ws)
      },
      closing
    ))

    /**
     * Client Established Connection With Socket Actor
     */
    def connected(ws: ActorRef[Command]): Behavior[Command] = Behaviors.receiveMessage(compose(
      {
        case Incoming(InitSession(timezone, locale)) =>
          val geoContext: GeoContext = GeoContext(locale, timezone)
          ctx.pipeToSelf(createNewSession(geoContext)) {
            case Success(value) => CreateNewSessionSuccess(value, geoContext)
            case Failure(exception) => CreateNewSessionFailure(exception)
          }
          Behaviors.same

        case CreateNewSessionSuccess(session, geo) =>
          inSession(session, geo, ws)
      },
      closing,
      outgoing(ws)
    ))

    /**
     * Session Begun With Client
     */
    def inSession(
      session: SessionContext,
      geo: GeoContext,
      ws: ActorRef[Command]
    ): Behavior[Command] = Behaviors.logMessages(Behaviors.setup { _ =>
      ws ! Outgoing(SetSession(
        User(
          userId = session.user.userId.get,
          username = session.userMeta.username,
          email = session.userMeta.email,
          token = session.token
        ),
        session.providerName,
        session.token,
      ))

      metrics.incSession(
        session.providerId,
        session.providerName,
        geo.timezone,
        geo.country,
      )

      singletons.gridCoordinator ! GridCoordinator.AddListener(
        session.providerId,
        gridAdapter,
      )

      def start(): Behavior[Command] = Behaviors.receiveMessage(compose(
        {
          case Incoming(JoinRoom(airingId)) =>
            lobby.join(
              airingId,
              session.user.userId.get,
              session.userMeta.username,
              lobbyAdapter
            )
            Behaviors.same

          case InRoom.RoomJoined(airingId, roomId) =>
            ctx.self ! Outgoing(RoomJoined(airingId))
            inRoom(
              session,
              geo,
              ws,
              roomId
            )
        },
        closing,
        outgoing(ws)
      ))

      start()
    })

    def inRoom(
      session: SessionContext,
      geo: GeoContext,
      ws: ActorRef[Command],
      roomId: UUID
    ): Behavior[Command] = Behaviors.receiveMessage(compose(
      {
        case InRoom.SetParticipants(participants) =>
          ctx.self ! Outgoing(SetParticipants(
            participants.map(rp => Participant(
              rp.userId,
              rp.username
            )).toSeq
          ))
          Behaviors.same
        case InRoom.AddParticipant(participant) =>
          ctx.self ! Outgoing(AddParticipant(Participant(
            participant.userId,
            participant.username
          )))
          Behaviors.same
        case InRoom.RemoveParticipant(participant) =>
          ctx.self ! Outgoing(RemoveParticipant(Participant(
            participant.userId,
            participant.username
          )))
          Behaviors.same
      },
      closing,
      outgoing(ws)
    ))

    run()
  }

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
      mutes <- getUserMutes(user.userId.get)
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
      token = token,
      muted = mutes
    )
  }) recoverWith {
    case ex: Throwable =>
      System.out.println(s"----- ERROR: ${ex.getMessage}")
      Future.failed(ex)
  }
}
