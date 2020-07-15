package com.couchmate.api.ws

import java.time.Duration
import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
import com.couchmate.Server
import com.couchmate.api.JwtProvider
import com.couchmate.api.ws.protocol._
import com.couchmate.api.ws.util.MessageMonitor
import com.couchmate.common.dao._
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.User
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.data.{UserActivity, UserActivityType, UserMeta, UserProvider, UserRole, User => InternalUser}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteDefaultProvider
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.{Chatroom, RoomParticipant}
import com.couchmate.util.akka.AkkaUtils
import com.couchmate.util.akka.extensions.{DatabaseExtension, PromExtension, RoomExtension, SingletonExtension}
import com.github.halfmatthalfcat.moniker.Moniker
import com.neovisionaries.i18n.CountryCode
import io.prometheus.client.Histogram

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object WSClient
  extends AkkaUtils
  with JwtProvider
  with UserDAO
  with UserMetaDAO
  with ProviderDAO
  with UserProviderDAO
  with UserMuteDAO
  with UserActivityDAO
  with GridDAO {
  import Commands._

  val moniker: Moniker = Moniker()

  def apply()(
    implicit
    ec: ExecutionContext,
    context: ActorContext[Server.Command]
  ): Behavior[Command] = Behaviors.setup { implicit ctx =>
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
      case Complete | Connected.LogoutSuccess =>
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
            case Success(value) => Connected.CreateNewSessionSuccess(value, geoContext)
            case Failure(exception) => Connected.CreateNewSessionFailure(exception)
          }
          Behaviors.same

        case Incoming(resume: RestoreSession) =>
          val geoContext: GeoContext = GeoContext(
            resume.locale,
            resume.timezone,
            resume.region
          )
          ctx.pipeToSelf(resumeSession(resume, geoContext)) {
            case Success(value) if resume.roomId.nonEmpty =>
              Connected.RestoreRoomSessionSuccess(value, geoContext, resume.roomId.get)
            case Success(value) =>
              Connected.RestoreSessionSuccess(value, geoContext)
            case Failure(exception) if resume.roomId.nonEmpty =>
              Connected.RestoreRoomSessionFailure(exception)
            case Failure(exception) =>
              ctx.log.error("Failed to restore session", exception)
              Connected.RestoreSessionFailure(exception)
          }
          Behaviors.same

        case Connected.CreateNewSessionSuccess(session, geo) =>
          inSession(session, geo, ws, init = true)
        case Connected.RestoreSessionSuccess(session, geo) =>
          inSession(session, geo, ws, init = true)
        case Connected.RestoreRoomSessionSuccess(session, geo, airingId) =>
          inSession(session, geo, ws, init = true, Some(airingId))
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
      init: Boolean = false,
      roomRestore: Option[UUID] = None,
    ): Behavior[Command] = Behaviors.setup { _ =>

      if (init && roomRestore.nonEmpty && session.roomIsOpen(roomRestore.get)) {
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

        lobby.join(
          roomRestore.get,
          session.user.userId.get,
          session.userMeta.username,
          chatAdapter
        )
      } else if (init) {
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

        ws ! Outgoing(UpdateGrid(session.grid))

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
            if (session.roomIsOpen(airingId)) {
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
              session.setAiringsFromGrid(grid),
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
              ),
              metrics.startTimeInRoom()
            )

          case Complete | Closed =>
            ctx.log.debug(s"Logging out ${session.user.userId.get}")
            metrics.decSession(
              session.providerId,
              session.providerName,
              geo.timezone,
              geo.country,
            )
            ctx.pipeToSelf(addUserActivity(UserActivity(
              session.user.userId.get,
              UserActivityType.Logout,
            ))) {
              case Success(_) => Connected.LogoutSuccess
              case Failure(exception) => Connected.LogoutFailure(exception)
            }
            Behaviors.same
        },
        closing,
        outgoing(ws)
      ))
    }

    def inRoom(
      session: SessionContext,
      geo: GeoContext,
      ws: ActorRef[Command],
      room: RoomContext,
      timer: Histogram.Timer,
      rejoining: Boolean = false
    ): Behavior[Command] = Behaviors.setup { _ =>

      if (!rejoining) {
        metrics.incAttendance(
          session.providerId,
          session.providerName,
          geo.timezone,
          geo.country,
        )
      }

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
            metrics.decAttendance(
              session.providerId,
              session.providerName,
              geo.timezone,
              geo.country,
            )
            timer.close()
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
              ),
              timer
            )
          case _: InRoom.RoomEnded =>
            messageMonitor ! MessageMonitor.Complete
            ctx.self ! Outgoing(RoomEnded)
            metrics.decAttendance(
              session.providerId,
              session.providerName,
              geo.timezone,
              geo.country,
            )
            timer.close()
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

          case Complete | Closed =>
            ctx.log.debug(s"Logging out ${session.user.userId.get}")
            metrics.decSession(
              session.providerId,
              session.providerName,
              geo.timezone,
              geo.country,
            )
            metrics.decAttendance(
              session.providerId,
              session.providerName,
              geo.timezone,
              geo.country,
            )
            timer.close()
            ctx.pipeToSelf(addUserActivity(UserActivity(
              session.user.userId.get,
              UserActivityType.Logout,
            ))) {
              case Success(_) => Connected.LogoutSuccess
              case Failure(exception) => Connected.LogoutFailure(exception)
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

  def resumeSession(
    resume: RestoreSession,
    geo: GeoContext
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[Command]
  ): Future[SessionContext] =
    validateToken(resume.token) match {
      case Failure(exception) =>
        ctx.log.warn(s"Got bad token: ${exception.getMessage}")
        createNewSession(geo)
      case Success(value) => getUser(value) flatMap {
        case None =>
          ctx.log.warn(s"Couldn't find user $value")
          createNewSession(geo)
        case Some(user @ InternalUser(Some(userId), _, _, _, _)) => for {
          userMeta <- getUserMeta(userId).map(_.getOrElse(
            throw new RuntimeException(s"Could not find userMeta for $userId")
          ))
          mutes <- getUserMutes(userId)
          userProvider <- getUserProvider(userId).map(_.getOrElse(
            throw new RuntimeException(s"Could not find userProvider for $userId")
          ))
          provider <- getProvider(userProvider.providerId).map(_.getOrElse(
            throw new RuntimeException(s"Could not find provider for $userId (${userProvider.providerId})")
          ))
          grid <- getGrid(userProvider.providerId)
          _ <- addUserActivity(UserActivity(
            userId,
            UserActivityType.Login
          ))
        } yield SessionContext(
          user = user,
          userMeta = userMeta,
          providerId = userProvider.providerId,
          providerName = provider.name,
          token = resume.token,
          muted = mutes,
          airings = Set.empty,
          grid = grid
        ).setAiringsFromGrid(grid)
      }
    }

  def createNewSession(context: GeoContext)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SessionContext] = {
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
      _ <- addUserActivity(UserActivity(
        user.userId.get,
        UserActivityType.Login
      ))
      grid <- getGrid(defaultProvider.value)
    } yield SessionContext(
      user = user,
      userMeta = userMeta,
      providerId = provider.get.providerId.get,
      providerName = provider.get.name,
      token = token,
      muted = mutes,
      airings = Set.empty,
      grid = grid
    ).setAiringsFromGrid(grid)
  }
}
