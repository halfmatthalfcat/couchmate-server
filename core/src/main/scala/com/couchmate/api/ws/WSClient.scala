package com.couchmate.api.ws

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
import com.couchmate.Server
import com.couchmate.api.ws.actions.{SessionActions, UserActions}
import com.couchmate.api.ws.protocol._
import com.couchmate.api.ws.util.{ConnectionMonitor, MessageMonitor}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.data.{UserActivity, UserActivityType, UserRole}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.{Chatroom, RoomParticipant}
import com.couchmate.util.akka.AkkaUtils
import com.couchmate.util.akka.extensions._
import io.prometheus.client.Histogram

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object WSClient
  extends AkkaUtils {
  import Commands._

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
      case Connected.LogoutFailure(ex) =>
        ctx.log.error(s"Failed to logout", ex)
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

    def ping(connMon: ActorRef[ConnectionMonitor.Command]): PartialCommand = {
      case Incoming(Pong) =>
        connMon ! ConnectionMonitor.ReceivePong
        Behaviors.same
    }

    /**
     * Initial Client State
     */
    def run(): Behavior[Command] = Behaviors.receiveMessage(compose(
      {
        case SocketConnected(ws) =>
          val connectionMonitor: ActorRef[ConnectionMonitor.Command] =
            ctx.spawnAnonymous(ConnectionMonitor(ws, ctx.self))
          connected(ws, connectionMonitor)
      },
      closing
    ))

    /**
     * Client Established Connection With Socket Actor
     */
    def connected(
      ws: ActorRef[Command],
      connMon: ActorRef[ConnectionMonitor.Command]
    ): Behavior[Command] = Behaviors.receiveMessage(compose(
      {
        case Incoming(InitSession(timezone, locale, region, os, osVersion, brand, model)) =>
          val geoContext: GeoContext = GeoContext(locale, timezone, region)
          val deviceContext: DeviceContext = DeviceContext(os, osVersion, brand, model)
          ctx.pipeToSelf(SessionActions.createNewSession(geoContext, deviceContext)) {
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
          ctx.pipeToSelf(SessionActions.resumeSession(resume, geoContext, deviceContext)) {
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
          inSession(session, geo, device, ws, connMon, init = true)
        case Connected.RestoreSessionSuccess(session, geo, device) =>
          inSession(session, geo, device, ws, connMon, init = true)
        case Connected.RestoreRoomSessionSuccess(session, geo, device, airingId) =>
          inSession(session, geo, device, ws, connMon, init = true, Some(airingId))
      },
      closing,
      outgoing(ws),
      ping(connMon)
    ))

    /**
     * Session Begun With Client
     */
    def inSession(
      session: SessionContext,
      geo: GeoContext,
      device: DeviceContext,
      ws: ActorRef[Command],
      connMon: ActorRef[ConnectionMonitor.Command],
      init: Boolean = false,
      roomRestore: Option[String] = None,
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
            } else {
              ws ! Outgoing(RoomClosed)
            }
            Behaviors.same

          case Incoming(ValidateEmail(email)) =>
            val emailRegex = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])".r
            if (emailRegex.matches(email)) {
              ctx.pipeToSelf(UserActions.emailExists(email)) {
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
              ctx.pipeToSelf(UserActions.usernameExists(username)) {
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
              ctx.pipeToSelf(UserActions.registerAccount(session, register)) {
                case Success(_) => Connected.AccountRegistrationSent
                case Failure(ex) =>
                  ctx.log.error("Failed to register", ex)
                  Connected.AccountRegistrationSentFailed(ex)
              }
            }
            Behaviors.same

          case Incoming(verify: VerifyAccount) =>
            if (!session.user.verified) {
              ctx.pipeToSelf(UserActions.verifyAccount(session, device, verify.token)) {
                case Success(session) => Connected.AccountVerified(session)
                case Failure(ex) => Connected.AccountVerifiedFailed(ex)
              }
            }
            Behaviors.same

          case Incoming(Login(email, password)) =>
            ctx.pipeToSelf(SessionActions.login(session, email, password)) {
              case Success(session) => Connected.LoggedIn(session)
              case Failure(ex: Throwable) => Connected.LoggedInFailed(ex)
            }
            Behaviors.same

          case Incoming(Logout) =>
            ctx.pipeToSelf(SessionActions.createNewSession(geo, device)) {
              case Success(session) => Connected.LoggedIn(session)
              case Failure(ex: Throwable) => Connected.LoggedInFailed(ex)
            }
            Behaviors.same

          case Incoming(ForgotPassword(email)) =>
            ctx.pipeToSelf(UserActions.sendForgotPassword(email)) {
              case Success(_) => Connected.ForgotPasswordSent
              case Failure(ex) => Connected.ForgotPasswordSentFailed(ex)
            }
            Behaviors.same

          case Incoming(ForgotPasswordReset(password, token)) =>
            ctx.pipeToSelf(UserActions.forgotPassword(token, password)) {
              case Success(_) => Connected.ForgotPasswordComplete
              case Failure(ex) => Connected.ForgotPasswordFailed(ex)
            }
            Behaviors.same

          case Incoming(ResetPassword(currentPassword, newPassword)) =>
            ctx.pipeToSelf(UserActions.passwordReset(session, currentPassword, newPassword)) {
              case Success(_) => Connected.PasswordResetComplete
              case Failure(ex) => Connected.PasswordResetFailed(ex)
            }
            Behaviors.same

          case Incoming(UpdateUsername(username)) =>
            if (session.user.verified) {
              ctx.pipeToSelf(UserActions.updateUsername(session, username)) {
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
            ctx.pipeToSelf(UserActions.unmuteParticipant(session, userId)) {
              case Success(session) => Connected.ParticipantUnmuted(session)
              case Failure(ex) => Connected.ParticipantUnmutedFailed(ex)
            }
            Behaviors.same

          case Incoming(MuteWord(word)) =>
            ctx.pipeToSelf(UserActions.addWordBlock(session, word.toLowerCase)) {
              case Success(value) => Connected.WordBlocked(value)
              case Failure(exception) => Connected.WordBlockFailed(exception)
            }
            Behaviors.same
          case Incoming(UnmuteWord(word)) =>
            ctx.pipeToSelf(UserActions.removeWordBlock(session, word.toLowerCase)) {
              case Success(value) => Connected.WordUnblocked(value)
              case Failure(exception) => Connected.WordUnblockFailed(exception)
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
            inSession(session, geo, device, ws, connMon)
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
            inSession(session, geo, device, ws, connMon)
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
            inSession(session, geo, device, ws, connMon, init = true, None)
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
            inSession(session, geo, device, ws, connMon)

          case Connected.WordBlocked(session) =>
            ws ! Outgoing(UpdateWordMutes(session.wordMutes))
            inSession(session, geo, device, ws, connMon)
          case Connected.WordUnblocked(session) =>
            ws ! Outgoing(UpdateWordMutes(session.wordMutes))
            inSession(session, geo, device, ws, connMon)

          case Connected.UpdateGrid(grid) =>
            ctx.self ! Outgoing(UpdateGrid(grid))
            inSession(
              session.setAiringsFromGrid(grid),
              geo,
              device,
              ws,
              connMon,
            )

          case InRoom.RoomJoined(airingId, roomId) =>
            ctx.self ! Outgoing(RoomJoined(airingId))
            inRoom(
              session,
              geo,
              device,
              ws,
              connMon,
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
            ctx.pipeToSelf(UserActions.addUserActivity(UserActivity(
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
            Behaviors.stopped
        },
        closing,
        outgoing(ws),
        ping(connMon)
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
      connMon: ActorRef[ConnectionMonitor.Command],
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
            inSession(session, geo, device, ws, connMon)

          case Incoming(SendMessage(message)) =>
            messageMonitor ! MessageMonitor.ReceiveMessage(message)
            Behaviors.same

          case Incoming(MuteParticipant(participant)) =>
            ctx.pipeToSelf(UserActions.muteParticipant(session, participant)) {
              case Success(session) => InRoom.ParticipantMuted(session)
              case Failure(ex) => InRoom.ParticipantMutedFailed(ex)
            }
            Behaviors.same

          case Incoming(report: ReportParticipant) =>
            ctx.pipeToSelf(UserActions.reportParticipant(session, report)) {
              case Success(_) => InRoom.ParticipantReported
              case Failure(exception) => InRoom.ParticipantReportFailed(exception)
            }
            Behaviors.same
          case InRoom.ParticipantReported =>
            ws ! Outgoing(ReportSuccess)
            Behaviors.same
          case InRoom.ParticipantReportFailed(ex) =>
            ctx.log.error("Failed to report participant", ex)
            ws ! Outgoing(ReportFailed)
            Behaviors.same

          case InRoom.RoomRejoined(airingId, roomId) =>
            messageMonitor ! MessageMonitor.Complete
            inRoom(
              session,
              geo,
              device,
              ws,
              connMon,
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
            inSession(session, geo, device, ws, connMon)
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
            inRoom(session, geo, device, ws, connMon, room, timer)

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
            ctx.pipeToSelf(UserActions.addUserActivity(UserActivity(
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
            Behaviors.stopped
        },
        closing,
        outgoing(ws),
        ping(connMon)
      ))
    }

    run()
  }
}
