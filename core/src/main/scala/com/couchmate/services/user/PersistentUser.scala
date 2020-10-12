package com.couchmate.services.user

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import com.couchmate.api.ws.protocol.{External, LoginError, Protocol, RegisterAccountError}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.data.UserRole
import com.couchmate.services.GridCoordinator
import com.couchmate.services.GridCoordinator.GridUpdate
import com.couchmate.services.user.commands.{ConnectedCommands, EmptyCommands, InitialCommands, UserActions}
import com.couchmate.services.user.context.{GeoContext, UserContext}
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

  final case class WSMessage(
    message: Protocol
  ) extends Command

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

  // -- STATES

  sealed trait State

  final case object EmptyState extends State

  final case class InitialState(
    userContext: UserContext
  ) extends State

  final case class ConnectedState(
    userContext: UserContext,
    geo: GeoContext,
    ws: ActorRef[WSPersistentActor.Command]
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
    implicit val room: RoomExtension =
      RoomExtension(ctx.system)
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
        ) => command match {
          case Connect(geo, ws) => InitialCommands.connect(userContext, geo, ws)
          case WSMessage(message) => message match {
            case _ =>
              ctx.log.debug(s"${userId} initial but stashing ${command}")
              Effect.stash()
          }
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
            InitialCommands.connect(userContext, geo, ws)
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
            ConnectedCommands.loggedIn(userId, geo, ws)
          case LogInFailed(LoginError(cause)) =>
            Effect.none.thenRun(_ => ws ! WSPersistentActor.OutgoingMessage(External.LoginFailure(cause)))
          case LoggedOut(userId) =>
            ctx.log.debug(s"Logging out of ${userContext.user.userId.get} to ${userId}")
            ConnectedCommands.loggedOut(userId, geo, ws)
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
            InitialState(userContext)
        }
        case state @ InitialState(userContext) => event match {
          case Connected(geo, ws) => ConnectedState(
            userContext, geo, ws
          )
        }
        case state @ ConnectedState(userContext, geo, ws) => event match {
          case Connected(geo, ws) => ConnectedState(
            userContext, geo, ws
          )
          case Disconnected => InitialState(userContext)
          case UserContextSet(userContext) => state.copy(
            userContext = userContext
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
