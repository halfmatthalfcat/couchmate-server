package com.couchmate.api.ws

import java.time.Duration

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
import com.couchmate.Server
import com.couchmate.api.JwtProvider
import com.couchmate.common.models.api.grid.GridAiring
import com.couchmate.common.models.api.room.Participant
import com.couchmate.api.ws.Commands.Connected.{CreateNewSessionFailure, CreateNewSessionSuccess}
import com.couchmate.api.ws.protocol._
import com.couchmate.api.ws.util.MessageMonitor
import com.couchmate.common.models.api.User
import com.couchmate.common.models.thirdparty.gracenote.GracenoteDefaultProvider
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.dao._
import com.couchmate.common.models.data.{RoomStatusType, UserMeta, UserProvider, UserRole, User => InternalUser}
import com.couchmate.common.models.api.User
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.{Chatroom, RoomParticipant}
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
    implicit val materializer: Materializer = Materializer(ctx.system)
    implicit val db: Database = DatabaseExtension(ctx.system).db

    val metrics: PromExtension =
      PromExtension(ctx.system)
    val lobby: RoomExtension =
      RoomExtension(ctx.system)
    val singletons: SingletonExtension =
      SingletonExtension(ctx.system)

    val gridAdapter: ActorRef[GridCoordinator.Command] = ctx.messageAdapter {
      case GridUpdate(grid) => Connected.UpdateGrid(grid)
    }

    val chatAdapter: ActorRef[Chatroom.Command] = ctx.messageAdapter {
      case Chatroom.RoomJoined(airingId, roomId) => InRoom.RoomJoined(airingId, roomId)
      case Chatroom.RoomRejoined(airingId, roomId) => InRoom.RoomRejoined(airingId, roomId)
      case Chatroom.RoomEnded(airingId, roomId) => InRoom.RoomEnded(airingId, roomId)
      case Chatroom.RoomParticipants(participants) => InRoom.SetParticipants(participants)
      case Chatroom.ParticipantJoined(participant) => InRoom.AddParticipant(participant)
      case Chatroom.ParticipantLeft(participant) => InRoom.RemoveParticipant(participant)
      case Chatroom.MessageSent(participant, message) => Messaging.MessageSent(participant, message)
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
        case Incoming(InitSession(timezone, locale, region)) =>
          val geoContext: GeoContext = GeoContext(locale, timezone, region)
          ctx.pipeToSelf(createNewSession(geoContext)) {
            case Success(value) => CreateNewSessionSuccess(value, geoContext)
            case Failure(exception) => CreateNewSessionFailure(exception)
          }
          Behaviors.same

        case CreateNewSessionSuccess(session, geo) =>
          inSession(session, geo, ws, init = true)
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
      ws: ActorRef[Command],
      init: Boolean = false
    ): Behavior[Command] = Behaviors.setup { _ =>

      if (init) {
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
      }

      Behaviors.receiveMessage(compose(
        {
          case Incoming(JoinRoom(airingId)) =>
            val roomReady: Boolean = session.airings.exists { airing =>
              airing.airingId == airingId &&
              (
                airing.status == RoomStatusType.PreGame ||
                airing.status == RoomStatusType.Open
              )
            }
            ctx.log.debug(s"${airingId} exists for user ${session.user.userId.get}: ${roomReady}")
            if (roomReady) {
              lobby.join(
                airingId,
                session.user.userId.get,
                session.userMeta.username,
                chatAdapter
              )
            }
            Behaviors.same

          case Connected.UpdateGrid(grid) =>
            ctx.self ! Outgoing(UpdateGrid(grid))
            inSession(
              session.copy(
                airings = grid.pages.foldLeft(Set.empty[GridAiring]) {
                  case (a1, gridPage) => a1 ++ gridPage.channels.foldLeft(Set.empty[GridAiring]) {
                    case (a2, channel) => a2 ++ channel.airings
                  }
                }
              ),
              geo,
              ws
            )

          case InRoom.RoomJoined(airingId, roomId) =>
            ctx.self ! Outgoing(RoomJoined(airingId))
            inRoom(
              session,
              geo,
              ws,
              RoomContext(
                airingId,
                roomId
              )
            )
        },
        closing,
        outgoing(ws)
      ))
    }

    def inRoom(
      session: SessionContext,
      geo: GeoContext,
      ws: ActorRef[Command],
      room: RoomContext
    ): Behavior[Command] = Behaviors.setup { _ =>

      val messageMonitorAdapter: ActorRef[MessageMonitor.Command] =
        ctx.messageAdapter {
          case MessageMonitor.LockSending(duration) =>
            Outgoing(LockSending(duration))
          case MessageMonitor.UnlockSending =>
            Outgoing(UnlockSending)
        }

      val messageMonitor: ActorRef[MessageMonitor.Command] =
        ctx.spawnAnonymous(MessageMonitor(
          session,
          room,
          messageMonitorAdapter,
          chatAdapter
        ))

      singletons.gridCoordinator ! GridCoordinator.RemoveListener(
        session.providerId,
        gridAdapter,
      )

      Behaviors.receiveMessage(compose(
        {
          case Incoming(LeaveRoom) =>
            lobby.leave(
              room.airingId,
              room.roomId,
              RoomParticipant(
                session.user.userId.get,
                session.userMeta.username,
                chatAdapter
              )
            )
            messageMonitor ! MessageMonitor.Complete
            singletons.gridCoordinator ! GridCoordinator.AddListener(
              session.providerId,
              gridAdapter,
            )
            inSession(session, geo, ws)
          case Incoming(SendMessage(message)) =>
            messageMonitor ! MessageMonitor.ReceiveMessage(message)
            Behaviors.same
          case InRoom.RoomRejoined(airingId, roomId) =>
            messageMonitor ! MessageMonitor.Complete
            inRoom(
              session,
              geo,
              ws,
              RoomContext(
                airingId,
                roomId
              )
            )
          case _: InRoom.RoomEnded =>
            messageMonitor ! MessageMonitor.Complete
            ctx.self ! Outgoing(RoomEnded)
            inSession(session, geo, ws)
          case InRoom.SetParticipants(participants) =>
            ctx.self ! Outgoing(SetParticipants(
              participants
                .map(rp => Participant(
                  rp.userId,
                  rp.username
                ))
                .filterNot(p => session.muted.contains(p.userId))
                .toSeq
            ))
            Behaviors.same
          case InRoom.AddParticipant(participant) =>
            if (!session.muted.contains(participant.userId)) {
              ctx.self ! Outgoing(AddParticipant(Participant(
                participant.userId,
                participant.username
              )))
            }
            Behaviors.same
          case InRoom.RemoveParticipant(participant) =>
            if (!session.muted.contains(participant.userId)) {
              ctx.self ! Outgoing(RemoveParticipant(Participant(
                participant.userId,
                participant.username
              )))
            }
            Behaviors.same
          case Messaging.MessageSent(participant, message) =>
            if (!session.muted.contains(participant.userId)) {
              ctx.self ! Outgoing(RoomMessage(
                Participant(
                  participant.userId,
                  participant.username
                ),
                isSelf = session.user.userId.contains(participant.userId),
                message
              ))
            }
            Behaviors.same
        },
        closing,
        outgoing(ws)
      ))
    }

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
      muted = mutes,
      airings = Set.empty
    )
  }) recoverWith {
    case ex: Throwable =>
      System.out.println(s"----- ERROR: ${ex.getMessage}")
      Future.failed(ex)
  }
}
