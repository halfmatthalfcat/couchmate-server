package com.couchmate.api.ws

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
import com.couchmate.Server
import com.couchmate.api.ws.protocol.RegisterAccountErrorCause.EmailExists
import com.couchmate.api.ws.protocol._
import com.couchmate.api.ws.util.MessageMonitor
import com.couchmate.common.dao._
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.data.{UserActivity, UserActivityType, UserMeta, UserMute, UserPrivate, UserProvider, UserRole, User => InternalUser}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteDefaultProvider
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.{Chatroom, RoomParticipant}
import com.couchmate.util.akka.AkkaUtils
import com.couchmate.util.akka.extensions._
import com.couchmate.util.jwt.Jwt.ExpiredJwtError
import com.github.halfmatthalfcat.moniker.Moniker
import com.neovisionaries.i18n.CountryCode
import io.prometheus.client.Histogram

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object WSClient
  extends AkkaUtils
  with UserDAO
  with UserMetaDAO
  with ProviderDAO
  with UserProviderDAO
  with UserPrivateDAO
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
    implicit val mail: MailExtension =
      MailExtension(ctx.system)
    implicit val jwt: JwtExtension =
      JwtExtension(ctx.system)

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
      case Chatroom.OutgoingRoomMessage(message) => Messaging.OutgoingRoomMessage(message)
      case Chatroom.MessageReplay(messages) => InRoom.MessageReplay(messages)
    }

    val messageMonitorAdapter: ActorRef[MessageMonitor.Command] =
      ctx.messageAdapter {
        case MessageMonitor.LockSending(duration) =>
          Outgoing(LockSending(duration))
        case MessageMonitor.UnlockSending =>
          Outgoing(UnlockSending)
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
    }

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
        case Incoming(InitSession(timezone, locale, region, os, osVersion, brand, model)) =>
          val geoContext: GeoContext = GeoContext(locale, timezone, region)
          val deviceContext: DeviceContext = DeviceContext(os, osVersion, brand, model)
          ctx.pipeToSelf(createNewSession(geoContext, deviceContext)) {
            case Success(value) => Connected.CreateNewSessionSuccess(value, geoContext, deviceContext)
            case Failure(exception) => Connected.CreateNewSessionFailure(exception)
          }
          Behaviors.same

        case Incoming(resume: RestoreSession) =>
          val geoContext: GeoContext = GeoContext(
            resume.locale,
            resume.timezone,
            resume.region
          )
          val deviceContext: DeviceContext = DeviceContext(
            resume.os,
            resume.osVersion,
            resume.brand,
            resume.model
          )
          ctx.pipeToSelf(resumeSession(resume, geoContext, deviceContext)) {
            case Success(value) if resume.roomId.nonEmpty =>
              Connected.RestoreRoomSessionSuccess(value, geoContext, deviceContext, resume.roomId.get)
            case Success(value) =>
              Connected.RestoreSessionSuccess(value, geoContext, deviceContext)
            case Failure(exception) if resume.roomId.nonEmpty =>
              Connected.RestoreRoomSessionFailure(exception)
            case Failure(exception) =>
              ctx.log.error("Failed to restore session", exception)
              Connected.RestoreSessionFailure(exception)
          }
          Behaviors.same

        case Connected.CreateNewSessionSuccess(session, geo, device) =>
          inSession(session, geo, device, ws, init = true)
        case Connected.RestoreSessionSuccess(session, geo, device) =>
          inSession(session, geo, device, ws, init = true)
        case Connected.RestoreRoomSessionSuccess(session, geo, device, airingId) =>
          inSession(session, geo, device, ws, init = true, Some(airingId))
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
      device: DeviceContext,
      ws: ActorRef[Command],
      init: Boolean = false,
      roomRestore: Option[UUID] = None,
    ): Behavior[Command] = Behaviors.setup { _ =>

      if (init && roomRestore.nonEmpty && session.roomIsOpen(roomRestore.get)) {
        ws ! Outgoing(SetSession(
          session.getClientUser,
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
          session.getClientUser,
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

          case Incoming(ValidateEmail(email)) =>
            val emailRegex = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])".r
            if (emailRegex.matches(email)) {
              ctx.pipeToSelf(emailExists(email)) {
                case Success(exists) => Connected.EmailValidated(
                  exists,
                  valid = true
                )
                case Failure(ex) => Connected.EmailValidatedFailed(ex)
              }
            } else {
              ctx.self ! Connected.EmailValidated(
                exists = false,
                valid = false
              )
            }
            Behaviors.same

          case Incoming(ValidateUsername(username)) =>
            val usernameRegex = "^[a-zA-Z0-9]{1,16}$".r
            if (usernameRegex.matches(username)) {
              ctx.pipeToSelf(usernameExists(username)) {
                case Success(exists) => Connected.UsernameValidated(
                  exists,
                  valid = true
                )
                case Failure(ex) => Connected.UsernameValidatedFailed(ex)
              }
            } else {
              ctx.self ! Connected.UsernameValidated(
                exists = false,
                valid = false
              )
            }
            Behaviors.same

          case Incoming(register: RegisterAccount) =>
            if (session.user.role == UserRole.Anon) {
              ctx.pipeToSelf(registerAccount(session, register)) {
                case Success(_) => Connected.AccountRegistrationSent
                case Failure(ex) =>
                  ctx.log.error("Failed to register", ex)
                  Connected.AccountRegistrationSentFailed(ex)
              }
            }
            Behaviors.same

          case Incoming(verify: VerifyAccount) =>
            if (!session.user.verified) {
              ctx.pipeToSelf(verifyAccount(session, device, verify.token)) {
                case Success(session) => Connected.AccountVerified(session)
                case Failure(ex) => Connected.AccountVerifiedFailed(ex)
              }
            }
            Behaviors.same

          case Incoming(Login(email, password)) =>
            ctx.pipeToSelf(login(session, email, password)) {
              case Success(session) => Connected.LoggedIn(session)
              case Failure(ex: Throwable) => Connected.LoggedInFailed(ex)
            }
            Behaviors.same

          case Incoming(Logout) =>
            ctx.pipeToSelf(createNewSession(geo, device)) {
              case Success(session) => Connected.LoggedIn(session)
              case Failure(ex: Throwable) => Connected.LoggedInFailed(ex)
            }
            Behaviors.same

          case Incoming(ForgotPassword(email)) =>
            ctx.pipeToSelf(sendForgotPassword(email)) {
              case Success(_) => Connected.ForgotPasswordSent
              case Failure(ex) => Connected.ForgotPasswordSentFailed(ex)
            }
            Behaviors.same

          case Incoming(ForgotPasswordReset(password, token)) =>
            ctx.pipeToSelf(forgotPassword(token, password)) {
              case Success(_) => Connected.ForgotPasswordComplete
              case Failure(ex) => Connected.ForgotPasswordFailed(ex)
            }
            Behaviors.same

          case Incoming(ResetPassword(currentPassword, newPassword)) =>
            ctx.pipeToSelf(passwordReset(session, currentPassword, newPassword)) {
              case Success(_) => Connected.PasswordResetComplete
              case Failure(ex) => Connected.PasswordResetFailed(ex)
            }
            Behaviors.same

          case Incoming(UpdateUsername(username)) =>
            if (session.user.verified) {
              ctx.pipeToSelf(updateUsername(session, username)) {
                case Success(session) => Connected.UsernameUpdated(session)
                case Failure(ex) => Connected.UsernameUpdatedFailed(ex)
              }
            } else {
              ws ! Outgoing(UpdateUsernameFailure(
                UpdateUsernameErrorCause.AccountNotRegistered
              ))
            }
            Behaviors.same

          case Incoming(UnmuteParticipant(userId)) =>
            ctx.pipeToSelf(unmuteParticipant(session, userId)) {
              case Success(session) => Connected.ParticipantUnmuted(session)
              case Failure(ex) => Connected.ParticipantUnmutedFailed(ex)
            }
            Behaviors.same

          case Connected.AccountRegistrationSent =>
            ws ! Outgoing(RegisterAccountSentSuccess)
            metrics.incRegistered(
              geo.timezone,
              geo.country
            )
            Behaviors.same
          case Connected.AccountRegistrationSentFailed(ex) => ex match {
            case RegisterAccountError(cause) =>
              ws ! Outgoing(RegisterAccountSentFailure(cause))
            case _ =>
              ws ! Outgoing(RegisterAccountSentFailure(RegisterAccountErrorCause.UnknownError))
          }
            Behaviors.same

          case Connected.AccountVerified(session) =>
            ws ! Outgoing(VerifyAccountSuccess(session.getClientUser))
            inSession(session, geo, device, ws)
          case Connected.AccountVerifiedFailed(ex) => ex match {
            case RegisterAccountError(cause) =>
              ws ! Outgoing(VerifyAccountFailed(cause))
              Behaviors.same
            case _ =>
              ws ! Outgoing(VerifyAccountFailed(
                RegisterAccountErrorCause.UnknownError
              ))
              Behaviors.same
          }

          case Connected.EmailValidated(exists, valid) =>
            ws ! Outgoing(ValidateEmailResponse(exists, valid))
            Behaviors.same

          case Connected.UsernameValidated(exists, valid) =>
            ws ! Outgoing(ValidateUsernameResponse(exists, valid))
            Behaviors.same

          case Connected.UsernameUpdated(session) =>
            ws ! Outgoing(UpdateUsernameSuccess(session.getClientUser))
            inSession(session, geo, device, ws)
          case Connected.UsernameUpdatedFailed(ex) => ex match {
            case UpdateUsernameError(cause) =>
              ws ! Outgoing(UpdateUsernameFailure(cause))
            case _ =>
              ws ! Outgoing(UpdateUsernameFailure(
                UpdateUsernameErrorCause.Unknown
              ))
          }
            Behaviors.same

          case Connected.LoggedIn(session) =>
            inSession(session, geo, device, ws, init = true, None)
          case Connected.LoggedInFailed(ex) => ex match {
            case LoginError(cause) =>
              ws ! Outgoing(LoginFailure(cause))
            case _ =>
              ws ! Outgoing(LoginFailure(LoginErrorCause.Unknown))
          }
            Behaviors.same

          case Connected.ForgotPasswordSent =>
            ws ! Outgoing(ForgotPasswordResponse(true))
            Behaviors.same
          case Connected.ForgotPasswordSentFailed(ex) =>
            ws ! Outgoing(ForgotPasswordResponse(false))
            Behaviors.same

          case Connected.ForgotPasswordComplete =>
            ws ! Outgoing(ForgotPasswordResetSuccess)
            Behaviors.same
          case Connected.ForgotPasswordFailed(ex) => ex match {
            case ForgotPasswordError(cause) =>
              ws ! Outgoing(ForgotPasswordResetFailed(cause))
              Behaviors.same
            case ex: Throwable =>
              ws ! Outgoing(ForgotPasswordResetFailed(
                ForgotPasswordErrorCause.Unknown
              ))
              Behaviors.same
          }

          case Connected.PasswordResetComplete =>
            ws ! Outgoing(ResetPasswordSuccess)
            Behaviors.same
          case Connected.PasswordResetFailed(ex) => ex match {
            case PasswordResetError(cause) =>
              ws ! Outgoing(ResetPasswordFailed(cause))
              Behaviors.same
            case ex: Throwable =>
              ws ! Outgoing(ResetPasswordFailed(
                PasswordResetErrorCause.Unknown
              ))
              Behaviors.same
          }

          case Connected.ParticipantUnmuted(session) =>
            ws ! Outgoing(UpdateMutes(session.mutes))
            inSession(session, geo, device, ws)

          case Connected.UpdateGrid(grid) =>
            ctx.self ! Outgoing(UpdateGrid(grid))
            inSession(
              session.setAiringsFromGrid(grid),
              geo,
              device,
              ws
            )

          case InRoom.RoomJoined(airingId, roomId) =>
            ctx.self ! Outgoing(RoomJoined(airingId))
            inRoom(
              session,
              geo,
              device,
              ws,
              RoomContext(
                airingId,
                roomId
              ),
              metrics.startTimeInRoom()
            )

          case Complete | Closed | ConnFailure(_) | Failed(_) =>
            ctx.log.debug(s"Logging out ${session.user.userId.get}")
            metrics.decSession(
              session.providerId,
              session.providerName,
              geo.timezone,
              geo.country,
            )
            ctx.pipeToSelf(addUserActivity(UserActivity(
              userId = session.user.userId.get,
              action = UserActivityType.Logout,
              os = device.os,
              osVersion = device.osVersion,
              brand = device.brand,
              model = device.model
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

    /**
     * TODO Need to come up with a better way to recreate state in here
     */
    def inRoom(
      session: SessionContext,
      geo: GeoContext,
      device: DeviceContext,
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
            inSession(session, geo, device, ws)
          case Incoming(SendMessage(message)) =>
            messageMonitor ! MessageMonitor.ReceiveMessage(message)
            Behaviors.same
          case Incoming(MuteParticipant(participant)) =>
            ctx.pipeToSelf(muteParticipant(session, participant)) {
              case Success(session) => InRoom.ParticipantMuted(session)
              case Failure(ex) => InRoom.ParticipantMutedFailed(ex)
            }
            Behaviors.same

          case InRoom.RoomRejoined(airingId, roomId) =>
            messageMonitor ! MessageMonitor.Complete
            inRoom(
              session,
              geo,
              device,
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
            inSession(session, geo, device, ws)
          case InRoom.SetParticipants(participants) =>
            ctx.self ! Outgoing(SetParticipants(
              participants
                .map(rp => Participant(
                  rp.userId,
                  rp.username
                ))
                .filterNot(p => session.mutes.exists(_.userId == p.userId))
                .toSeq
            ))
            Behaviors.same
          case InRoom.AddParticipant(participant) =>
            if (!session.mutes.exists(_.userId == participant.userId)) {
              ctx.self ! Outgoing(AddParticipant(Participant(
                participant.userId,
                participant.username
              )))
            }
            Behaviors.same
          case InRoom.RemoveParticipant(participant) =>
            if (!session.mutes.exists(_.userId == participant.userId)) {
              ctx.self ! Outgoing(RemoveParticipant(Participant(
                participant.userId,
                participant.username
              )))
            }
            Behaviors.same
          case InRoom.MessageReplay(messages) =>
            ctx.self ! Outgoing(MessageReplay(
              messages
                .filterNot(_.author.map(_.userId).exists(authorId => session.mutes.exists(_.userId == authorId)))
                .map(message => message.copy(
                  isSelf = message.author.exists(_.userId == session.user.userId.get)
                ))
            ))
            Behaviors.same
          case InRoom.ParticipantMuted(session) =>
            // TODO this is inefficient
            messageMonitor ! MessageMonitor.Complete
            ctx.self ! Outgoing(UpdateMutes(session.mutes))
            inRoom(session, geo, device, ws, room, timer)

          case Messaging.OutgoingRoomMessage(message) =>
            if (!session.mutes.exists(mute => message.author.exists(_.userId == mute.userId))) {
              ctx.self ! Outgoing(AppendMessage(
                message.copy(
                  isSelf = message.author.exists(_.userId == session.user.userId.get)
                )
              ))
            }
            Behaviors.same

          case Complete | Closed | ConnFailure(_) | Failed(_) =>
            messageMonitor ! MessageMonitor.Complete
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
              userId = session.user.userId.get,
              action = UserActivityType.Logout,
              os = device.os,
              osVersion = device.osVersion,
              brand = device.brand,
              model = device.model
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
        ctx.log.warn(s"Got bad token: ${exception.getMessage}")
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
      airings = Set.empty,
      grid = grid
    ).setAiringsFromGrid(grid)
  }

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

  def login(session: SessionContext, email: String, password: String)(
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
      userProvider <- getUserProvider(user.userId.get)
      provider <- getProvider(userProvider.get.providerId)
      grid <- getGrid(userProvider.get.providerId)
    } yield session.copy(
      user,
      userMeta.get,
      provider.get.providerId.get,
      provider.get.name,
      token,
      mutes,
      airings = Set(),
      grid
    ).setAiringsFromGrid(grid)
  }

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
}
