package com.couchmate.services.user

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import com.couchmate.api.ws.protocol.{External, ForgotPasswordError, LoginError, PasswordResetError, Protocol, RegisterAccountError, UpdateUsernameError}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.api.room.tenor.TenorGif
import com.couchmate.common.models.api.user.UserMute
import com.couchmate.common.models.data.{UserMeta, UserReportType, UserRole}
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.room.TenorService.{GetTenorTrending, SearchTenor}
import com.couchmate.services.room.{Chatroom, RoomId}
import com.couchmate.services.user.commands.{ConnectedCommands, EmptyCommands, InitialCommands, RoomCommands, UserActions}
import com.couchmate.services.user.context.{GeoContext, RoomContext, UserContext}
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{DatabaseExtension, JwtExtension, MailExtension, PromExtension, RoomExtension, SingletonExtension, UserExtension}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object PersistentUser {
  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("User")

  // -- COMMANDS

  sealed trait Command

  // Init this PersistentUser on first creation
  final case object Init extends Command
  final case class SetUserContext(userContext: UserContext) extends Command
  final case class SetUserContextFailed(ex: Throwable) extends Command

  // Connect this PersistentUser to a websocket-based actor
  final case class Connect(
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
  ) extends Command
  final case object Disconnect extends Command

  case class UpdateGrid(grid: Grid) extends Command

  case class EmailValidated(exists: Boolean, valid: Boolean) extends Command
  case class EmailValidationFailed(ex: Throwable) extends Command

  case class UsernameValidated(exists: Boolean, valid: Boolean) extends Command
  case class UsernameValidationFailed(ex: Throwable) extends Command

  case object AccountRegistered extends Command
  case class AccountRegistrationFailed(ex: Throwable) extends Command

  case class AccountVerified(userId: UUID) extends Command
  case class AccountVerificationFailed(ex: Throwable) extends Command

  case class LoggedIn(userId: UUID) extends Command
  case class LogInFailed(ex: Throwable) extends Command

  case class LoggedOut(userId: UUID) extends Command
  case class LogoutFailed(ex: Throwable) extends Command

  case object ForgotPasswordSent extends Command
  case class ForgotPasswordFailed(ex: Throwable) extends Command

  case object ForgotPasswordReset extends Command
  case class ForgotPasswordResetFailed(ex: Throwable) extends Command

  case object PasswordReset extends Command
  case class PasswordResetFailed(ex: Throwable) extends Command

  case class UpdateUsername(userMeta: UserMeta) extends Command
  case class UpdateUsernameFailed(ex: Throwable) extends Command

  case class MuteParticipant(mutes: Seq[UserMute]) extends Command
  case class MuteParticipantFailed(ex: Throwable) extends Command

  case class UnmuteParticipant(mutes: Seq[UserMute]) extends Command
  case class UnmuteParticipantFailed(ex: Throwable) extends Command

  case class MuteWord(mutes: Seq[String]) extends Command
  case class MuteWordFailed(ex: Throwable) extends Command

  case class UnmuteWord(mutes: Seq[String]) extends Command
  case class UnmuteWordFailed(ex: Throwable) extends Command

  case class ReportParticipant(
    userId: UUID,
    reportType: UserReportType,
    message: Option[String]
  ) extends Command
  case class ReportParticipantFailed(ex: Throwable) extends Command

  final case class WSMessage(
    message: Protocol
  ) extends Command
  final case class RoomMessage(
    message: Chatroom.Command
  ) extends Command

  final case class TenorTrending(keywords: Seq[String]) extends Command
  final case class TenorSearched(gifs: Seq[TenorGif]) extends Command

  // -- EVENTS

  sealed trait Event

  final case class UserContextSet(
    userContext: UserContext
  ) extends Event

  final case class Connected(
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
  ) extends Event
  final case object Disconnected extends Event

  final case class GridUpdated(grid: Grid) extends Event
  final case class UsernameUpdated(userMeta: UserMeta) extends Event
  final case class ParticipantMuted(mutes: Seq[UserMute]) extends Event
  final case class ParticipantUnmuted(mutes: Seq[UserMute]) extends Event
  final case class WordMuted(mutes: Seq[String]) extends Event
  final case class WordUnmuted(mutes: Seq[String]) extends Event
  final case class RoomJoined(airingId: String, roomId: RoomId) extends Event
  final case class HashRoomChanged(roomId: RoomId) extends Event
  final case object RoomLeft extends Event

  // -- STATES

  sealed trait State

  final case object EmptyState extends State

  final case class InitialState(
    userContext: UserContext,
    roomContext: Option[RoomContext]
  ) extends State

  final case class ConnectedState(
    userContext: UserContext,
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
  ) extends State

  final case class RoomState(
    userContext: UserContext,
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command],
    roomContext: RoomContext
  ) extends State

  def apply(
    userId: UUID,
    persistenceId: PersistenceId
  ): Behavior[Command] = Behaviors.setup { implicit ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext

    implicit val db: Database =
      DatabaseExtension(ctx.system).db
    implicit val jwt: JwtExtension =
      JwtExtension(ctx.system)
    implicit val metrics: PromExtension =
      PromExtension(ctx.system)
    implicit val lobby: RoomExtension =
      RoomExtension(ctx.system)
    implicit val singletons: SingletonExtension =
      SingletonExtension(ctx.system)
    implicit val mail: MailExtension =
      MailExtension(ctx.system)
    implicit val user: UserExtension =
      UserExtension(ctx.system)

    implicit val gridAdapter: ActorRef[GridCoordinator.Command] = ctx.messageAdapter {
      case GridUpdate(grid) => UpdateGrid(grid)
    }

    def commandHandler: (State, Command) => Effect[Event, State] =
      (state, command) => state match {
        /**
         * EmptyState: User has never initialized before.
         * This state should only ever exist once, when the user first
         * enters the system. After that, the state should be persisted
         * and subsequent activations of this actor should be in any other
         * state.
         */
        case EmptyState => command match {
          case SetUserContext(userContext) => EmptyCommands.setUserContext(userContext)
          case SetUserContextFailed(exception) => EmptyCommands.setUserContextFailed(exception)
          case _ =>
            ctx.log.debug(s"${userId} empty but stashing ${command}")
            Effect.stash()
        }
        /**
         * InitializedState: Actor has been created (user context set) however has not
         * connected to the outgoing Websocket-based actor.
         * This state encompasses a user that has completely logged out of the system
         * and is not receiving any messages, actively or passively.
         */
        case InitialState(
          userContext,
          roomContext,
        ) => command match {
          case Connect(geo, ws) =>
            InitialCommands.connect(
              userContext,
              geo,
              ws,
              roomContext
            )
          case WSMessage(message) => message match {
            case _ => Effect.stash()
          }
          case _ => Effect.unhandled
        }
        /**
         * ConnectedState: User is logged into the system.
         * User has either connected anonymously or logged in via credentials
         * and is ready to start executing actions against the system.
         */
        case ConnectedState(
          userContext,
          geo,
          ws,
        ) => command match {
          /**
           * The below commands either coming from within the cluster
           * or responses to actions taken by a user. Most of these
           * are not persisted to state but only serve as a request/reply.
           */
          case Connect(geo, ws) =>
            InitialCommands.connect(userContext, geo, ws, Option.empty)
          case Disconnect =>
            ConnectedCommands.disconnect(userContext, geo)
          case UpdateGrid(grid) =>
            ConnectedCommands.updateGrid(grid, ws)
          case validated: EmailValidated =>
            ConnectedCommands.emailValidated(validated, ws)
          case validated: UsernameValidated =>
            ConnectedCommands.usernameValidated(validated, ws)
          case AccountRegistered =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(External.RegisterAccountSentSuccess))
          case AccountRegistrationFailed(RegisterAccountError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(External.RegisterAccountSentFailure(cause)))
          case AccountVerified(_) =>
            ConnectedCommands.accountVerified(userContext)
          case AccountVerificationFailed(RegisterAccountError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(External.VerifyAccountFailed(cause)))
          case LoggedIn(userId) =>
            ConnectedCommands.loggedIn(userId, ws)
          case LogInFailed(LoginError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(External.LoginFailure(cause)))
          case LoggedOut(userId) =>
            ConnectedCommands.loggedOut(userId, ws)
          case ForgotPasswordSent =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.ForgotPasswordResponse(true)
            ))
          case ForgotPasswordFailed(_) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.ForgotPasswordResponse(false)
            ))
          case ForgotPasswordReset =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.ForgotPasswordResetSuccess
            ))
          case ForgotPasswordResetFailed(ForgotPasswordError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.ForgotPasswordResetFailed(cause)
            ))
          case PasswordReset =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.ResetPasswordSuccess
            ))
          case PasswordResetFailed(PasswordResetError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.ResetPasswordFailed(cause)
            ))
          case UpdateUsername(userMeta) =>
            ConnectedCommands.usernameUpdated(userMeta)
          case UpdateUsernameFailed(UpdateUsernameError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.UpdateUsernameFailure(cause)
            ))
          case MuteParticipant(mutes) =>
            ConnectedCommands.participantMuted(mutes)
          case MuteParticipantFailed(_) => Effect.none
          case UnmuteParticipant(mutes) =>
            ConnectedCommands.participantUnmuted(mutes)
          case UnmuteParticipantFailed(_) => Effect.none
          case MuteWord(mutes) =>
            ConnectedCommands.wordMuted(mutes)
          case MuteWordFailed(_) => Effect.none
          case UnmuteWord(mutes) =>
            ConnectedCommands.wordUnmuted(mutes)
          case UnmuteWordFailed(_) => Effect.none
          /**
           * WSMessage wraps _inbound_ messages via a Websocket
           * with the intention that a message will be relayed back
           * through the socket after the request has been completed,
           * successfully or unsuccessfully.
           *
           * Most of these commands follow verbNoun naming schemes
           * while their completed counterparts above follow nounVerb
           */
          case WSMessage(message) => message match {
            case External.Ping =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.Pong
              ))
            case External.ValidateEmail(email) =>
              ConnectedCommands.validateEmail(email)
            case External.ValidateUsername(username) =>
              ConnectedCommands.validateUsername(username)
            case External.RegisterAccount(email, password)
              if userContext.user.role == UserRole.Anon =>
              ConnectedCommands.registerAccount(userId, email, password)
            case External.VerifyAccount(token) =>
              ConnectedCommands.verifyAccount(userContext, token)
            case External.Login(email, password) =>
              ConnectedCommands.login(email, password)
            case External.Logout =>
              ConnectedCommands.logout(geo)
            case External.ForgotPassword(email) =>
              ConnectedCommands.forgotPassword(email)
            case External.ForgotPasswordReset(password, token) =>
              ConnectedCommands.forgotPasswordReset(password, token)
            case External.ResetPassword(currentPassword, newPassword) =>
              ConnectedCommands.resetPassword(
                userContext.user.userId.get,
                currentPassword,
                newPassword
              )
            case External.UpdateUsername(username) =>
              ConnectedCommands.updateUsername(
                userContext,
                username
              )
            case External.MuteParticipant(userId) =>
              ConnectedCommands.muteParticipant(
                userContext,
                userId,
              )
            case External.UnmuteParticipant(userId) =>
              ConnectedCommands.unmuteParticipant(
                userContext,
                userId
              )
            case External.MuteWord(word) =>
              ConnectedCommands.muteWord(
                userContext.user.userId.get,
                word
              )
            case External.UnmuteWord(word) =>
              ConnectedCommands.unmuteWord(
                userContext.user.userId.get,
                word
              )
            case External.JoinRoom(airingId, hash) if hash.forall(RoomCommands.hashValid) =>
              Effect.none.thenRun(_ => lobby.join(
                airingId,
                userContext,
                hash
              ))
            case _ => Effect.unhandled
          }
          /**
           * Any incoming Room Messages
           */
          case RoomMessage(message) => message match {
            case Chatroom.RoomJoined(airingId, roomId) =>
              RoomCommands.roomJoined(userContext, geo, airingId, roomId, ws)
            case Chatroom.RoomClosed =>
              RoomCommands.roomClosed(userContext, ws)
            case _ => Effect.stash()
          }
          case _ => Effect.unhandled
        }
        /**
         * RoomState: The user is connected and currently in a room
         */
        case RoomState(userContext, geo, ws, roomContext) => command match {
          /**
           * Any incoming Websocket messages while in-room
           */
          case Disconnect =>
            RoomCommands.disconnect(userContext, geo, roomContext)
          case TenorTrending(keywords) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.TenorTrendingResults(keywords)
            ))
          case TenorSearched(gifs) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
              External.TenorSearchResults(gifs)
            ))
          case WSMessage(message) => message match {
            case External.Ping =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.Pong
              ))
            case External.SendMessage(message) =>
              Effect.none.thenRun(_ => lobby.message(
                roomContext.airingId,
                roomContext.roomId,
                userContext.user.userId.get,
                message
              ))
            case External.SendGif(url) =>
              Effect.none.thenRun(_ => lobby.gif(
                roomContext.airingId,
                roomContext.roomId,
                userContext.user.userId.get,
                url
              ))
            case External.AddReaction(messageId, shortCode) =>
              Effect.none.thenRun(_ => lobby.addReaction(
                roomContext.airingId,
                roomContext.roomId,
                userContext.user.userId.get,
                messageId,
                shortCode
              ))
            case External.RemoveReaction(messageId, shortCode) =>
              Effect.none.thenRun(_ => lobby.removeReaction(
                roomContext.airingId,
                roomContext.roomId,
                userContext.user.userId.get,
                messageId,
                shortCode
              ))
            case External.LeaveRoom =>
              RoomCommands.roomLeft(
                userContext,
                geo,
                roomContext.airingId,
                roomContext.roomId
              )
            case External.GetTenorTrending =>
              Effect.none.thenRun(_ => singletons.tenorService ! GetTenorTrending(
                userContext.user.userId.get
              ))
            case External.TenorSearch(search) =>
              Effect.none.thenRun(_ => singletons.tenorService ! SearchTenor(
                userContext.user.userId.get,
                search
              ))
            case External.ChangeHashRoom(name) if RoomCommands.hashValid(name) =>
              Effect.none.thenRun(_ => lobby.changeHash(
                roomContext.airingId,
                userContext.user.userId.get,
                name
              ))
            case _ => Effect.unhandled
          }
          case RoomMessage(message) => message match {
            case Chatroom.RoomParticipants(participants) =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.SetParticipants(participants.filterNot(userContext.mutes.contains).toSeq)
              ))
            case Chatroom.ParticipantJoined(participant) =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.AddParticipant(participant)
              ))
            case Chatroom.ParticipantLeft(participant) =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.RemoveParticipant(participant)
              ))
            case Chatroom.HashRoomChanged(roomId) =>
              RoomCommands.hashRoomChanged(roomId, ws)
            case Chatroom.UpdateHashRooms(rooms) =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.UpdateHashRooms(rooms)
              ))
            case Chatroom.MessageReplay(messages) =>
              RoomCommands.messageReplay(
                userContext,
                messages,
                ws
              )
            case Chatroom.OutgoingRoomMessage(message) =>
              RoomCommands.sendMessage(
                userContext,
                message,
                ws
              )
            case Chatroom.UpdateRoomMessage(message) =>
              RoomCommands.updateMessage(
                userContext,
                message,
                ws
              )
            case Chatroom.ReactionAdded =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.AddReactionSuccess
              ))
            case Chatroom.ReactionRemoved =>
              Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(
                External.RemoveReactionSuccess
              ))
            case _ => Effect.unhandled
          }
          case _ => Effect.unhandled
        }
        case _ => Effect.unhandled
      }

    def eventHandler: (State, Event) => State =
      (state, event) => state match {
        case EmptyState => event match {
          case UserContextSet(userContext) =>
            InitialState(userContext, Option.empty)
        }
        case InitialState(userContext, _) => event match {
          case Connected(geo, ws) => ConnectedState(
            userContext, geo, ws
          )
        }
        case state @ ConnectedState(userContext, geo, ws) => event match {
          case Connected(geo, ws) => ConnectedState(
            userContext, geo, ws
          )
          case Disconnected => InitialState(
            userContext,
            Option.empty
          )
          case UserContextSet(userContext) => state.copy(
            userContext = userContext
          )
          case UsernameUpdated(userMeta) => state.copy(
            userContext = userContext.copy(
              userMeta = userMeta
            )
          )
          case ParticipantMuted(mutes) => state.copy(
            userContext = userContext.copy(
              mutes = mutes
            )
          )
          case ParticipantUnmuted(mutes) => state.copy(
            userContext = userContext.copy(
              mutes = mutes
            )
          )
          case WordMuted(mutes) => state.copy(
            userContext = userContext.copy(
              wordMutes = mutes
            )
          )
          case WordUnmuted(mutes) => state.copy(
            userContext = userContext.copy(
              wordMutes = mutes
            )
          )
          case RoomJoined(airingId, roomId) => RoomState(
            userContext,
            geo,
            ws,
            RoomContext(
              airingId,
              roomId
            )
          )
          case RoomLeft => ConnectedState(
            userContext,
            geo,
            ws
          )
        }
        case state @ RoomState(userContext, geo, ws, roomContext) => event match {
          case Disconnected => InitialState(
            userContext,
            Some(roomContext)
          )
          case RoomLeft => ConnectedState(
            userContext,
            geo,
            ws
          )
          case HashRoomChanged(roomId) => state.copy(
            roomContext = roomContext.copy(
              roomId = roomId
            )
          )
        }
      }

    EventSourcedBehavior(
      persistenceId,
      EmptyState,
      commandHandler,
      eventHandler
    ).receiveSignal {
      case (EmptyState, RecoveryCompleted) =>
        ctx.log.info(s"Starting $userId from empty")
        ctx.pipeToSelf(
          UserActions.createUserContext(userId)
        ) {
          case Success(userContext) => SetUserContext(userContext)
          case Failure(exception) => SetUserContextFailed(exception)
        }
    }.withRetention(
      RetentionCriteria.snapshotEvery(
        numberOfEvents = 100, keepNSnapshots = 2
      ).withDeleteEventsOnSnapshot
    )
  }

}
