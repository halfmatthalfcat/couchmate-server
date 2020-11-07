package com.couchmate.services.room

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.couchmate.common.dao.{AiringDAO, RoomActivityDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.room.Participant
import com.couchmate.common.models.api.room.message.{Message, SystemMessage, TextMessage, TextMessageWithLinks}
import com.couchmate.common.models.data.{AiringStatus, RoomActivity, RoomActivityType, RoomStatusType}
import com.couchmate.common.models.data.RoomStatusType.Closed
import com.couchmate.services.room.LinkScanner.ScanMessage
import com.couchmate.services.user.context.UserContext
import com.couchmate.util.akka.extensions.{DatabaseExtension, PromExtension, SingletonExtension, UserExtension}

import scala.concurrent.ExecutionContext
import scala.compat.java8.DurationConverters
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Chatroom
  extends AiringDAO
  with RoomActivityDAO {
  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Chatroom")

  sealed trait Command

  final case class JoinRoom(
    userContext: UserContext,
    hash: String = "general"
  ) extends Command
  final case class LeaveRoom(
    roomId: RoomId,
    userId: UUID,
  ) extends Command

  final case class SendMessage(
    roomId: RoomId,
    userId: UUID,
    message: String
  ) extends Command
  final case class SendGif(
    roomId: RoomId,
    userId: UUID,
    url: String
  ) extends Command
  final case class SendTextMessageWithLinks(
    roomId: RoomId,
    message: TextMessageWithLinks
  ) extends Command

  final case class AddReaction(
    roomId: RoomId,
    userId: UUID,
    messageId: String,
    shortCode: String
  ) extends Command
  final case class RemoveReaction(
    roomId: RoomId,
    userId: UUID,
    messageId: String,
    shortCode: String
  ) extends Command

  final case class RoomJoined(
    airingId: String,
    roomId: RoomId
  ) extends Command
  final case class RoomRejoined(
    airingId: String,
    roomId: RoomId
  ) extends Command
  final case class RoomEnded(
    airingId: String,
    roomId: RoomId
  ) extends Command
  final case class RoomParticipants(
    participants: Set[Participant]
  ) extends Command
  final case class ParticipantJoined(
    participant: Participant
  ) extends Command
  final case class ParticipantLeft(
    participant: Participant
  ) extends Command
  final case class ParticipantKicked(
    userId: UUID
  ) extends Command

  final case class MessageReplay(
    messages: List[Message]
  ) extends Command

  final case class OutgoingRoomMessage(
    message: Message
  ) extends Command
  final case class UpdateRoomMessage(
    message: Message
  ) extends Command

  final case object ReactionAdded extends Command
  final case object ReactionRemoved extends Command

  private final case class GetAiringSuccess(airing: AiringStatus) extends Command
  private final case class GetAiringFailure(err: Throwable) extends Command

  private final case object ShowEnding extends Command
  private final case object RoomEnding extends Command

  final case object RoomClosed extends Command
  final case object CloseRoom extends Command

  private sealed trait Event

  private final case class JoinedRoom(
    participant: Participant,
    hash: String
  ) extends Event
  private final case class LeftRoom(
    roomId: RoomId,
    userId: UUID
  ) extends Event
  private final case class SetAiringStatus(
    status: AiringStatus
  ) extends Event
  private final case class MessageReceived(
    roomId: RoomId,
    roomMessage: Message
  ) extends Event
  private final case class AddReactionReceived(
    roomId: RoomId,
    roomMessage: Message
  ) extends Event
  private final case class RemoveReactionReceived(
    roomId: RoomId,
    roomMessage: Message
  ) extends Event
  private final case object ClearAiring extends Event

  final case class State(
    airingId: String,
    status: Option[AiringStatus],
    hashes: Map[String, HashRoom]
  )

  def apply(airingId: String, persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { implicit ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database =
      DatabaseExtension(ctx.system).db
    implicit val metrics: PromExtension =
      PromExtension(ctx.system)
    implicit val user: UserExtension =
      UserExtension(ctx.system)
    implicit val singleton: SingletonExtension =
      SingletonExtension(ctx.system)

    Behaviors.withTimers { timers =>
      EventSourcedBehavior(
        persistenceId,
        State(
          airingId,
          None,
          Map(
            "general" -> HashRoom("general")
          )
        ),
        commandHandler(
          timers,
          metrics
        ),
        eventHandler
      ).receiveSignal {
        case (State(airingId, None, _), RecoveryCompleted) =>
          ctx.pipeToSelf(getAiringStatus(airingId)) {
            case Success(Some(value)) => GetAiringSuccess(value)
            case Success(None) => GetAiringFailure(new RuntimeException(s"Unable to get Airing for ${airingId}"))
            case Failure(ex) => GetAiringFailure(ex)
          }
      }
    }
  }

  private[this] def commandHandler(
    timers: TimerScheduler[Command],
    metrics: PromExtension
  )(
    implicit
    ctx: ActorContext[Command],
    userExtension: UserExtension,
    singletonExtension: SingletonExtension,
    db: Database
  ): (State, Command) => Effect[Event, State] =
    (prevState, command) => prevState match {
      case State(_, None, _) => command match {
        case GetAiringSuccess(status) => Effect
          .persist(SetAiringStatus(status))
          .thenRun((s: State) => {
            ctx.log.info(s"Setting timers for ${s.airingId}")
            ctx.log.info(Duration.between(
              LocalDateTime.now(ZoneId.of("UTC")),
              s.status.get.endTime.minusMinutes(
                s.status.get.duration - Math.round(s.status.get.duration * 0.9)
              ),
            ).toString)
            // Fire timer at around the 90% show completion mark
            timers.startSingleTimer(
              ShowEnding,
              DurationConverters.toScala(Duration.between(
                LocalDateTime.now(ZoneId.of("UTC")),
                s.status.get.endTime.minusMinutes(
                  s.status.get.duration - Math.round(s.status.get.duration * 0.9)
                ),
              )).max(FiniteDuration(0L, SECONDS))
            )
            // Fire timer at show end + 15 minutes to close the room
            timers.startSingleTimer(
              RoomEnding,
              DurationConverters.toScala(Duration.between(
                LocalDateTime.now(ZoneId.of("UTC")),
                s.status.get.endTime.plusMinutes(15),
              )).max(FiniteDuration(0L, SECONDS))
            )
          })
          .thenRun(_ => metrics.incRoom())
          .thenUnstashAll()
        case GetAiringFailure(ex) =>
          ctx.log.error("Couldn't get airing", ex)
          Effect.stop()
        case _ => Effect.stash()
      }
      case State(_, Some(status), _) => command match {
        case CloseRoom =>
          Effect.persist(ClearAiring).thenStop()
        case ShowEnding =>
          Effect.none
          .thenRun((s: State) => {
            ctx.log.info(s"Show ${s.airingId} is ending, firing")
            s.hashes.foreachEntry {
              case (_, namedRoom) =>
                namedRoom.broadcastAll(SystemMessage(
                  "This show is ending soon. You can still continue chatting for 15 minutes after."
                ))
            }
          })
        case RoomEnding => Effect
          .persist(SetAiringStatus(status.copy(
            status = RoomStatusType.Closed
          )))
          .thenRun((s: State) => {
            ctx.log.info(s"Room ${s.airingId} is ending, firing")
            s.hashes.foreachEntry {
              case (_, namedRoom) => namedRoom.rooms.foreach { room =>
                room.participants.foreach { participant =>
                  userExtension.roomMessage(
                    participant.userId,
                    RoomEnded(
                      s.airingId,
                      room.roomId
                    )
                  )
                  addRoomActivity(RoomActivity(
                    s.airingId,
                    participant.userId,
                    RoomActivityType.Kicked.Expired
                  ))
                }
              }
            }
          })
          .thenStop()
        case JoinRoom(userContext, hash) if (
          status.status == RoomStatusType.Open ||
          status.status == RoomStatusType.PreGame
        ) => Effect.persist(JoinedRoom(
          Participant(
            userContext.user.userId.get,
            userContext.userMeta.username,
            List.empty
          ), hash
        ))
         .thenRun((s: State) => userExtension.roomMessage(
           userContext.user.userId.get,
           RoomJoined(
             s.airingId,
             s.hashes(hash).getParticipantRoom(
               userContext.user.userId.get
             ).get.roomId
           )))
         .thenRun((s: State) => userExtension.roomMessage(
           userContext.user.userId.get,
           RoomParticipants(
             s.hashes(hash).getParticipantRoom(
               userContext.user.userId.get
             ).get.participants.toSet
           )
         ))
         .thenRun((s: State) => userExtension.roomMessage(
           userContext.user.userId.get,
           MessageReplay(s.hashes(hash).getParticipantRoom(
             userContext.user.userId.get
           ).get.messages)
         ))
         .thenRun((s: State) => s.hashes(hash).getParticipantRoom(
           userContext.user.userId.get
         ).get.participants.tail.foreach(p => userExtension.roomMessage(
           p.userId,
           ParticipantJoined(
             s.hashes(hash).rooms.head.participants.head
           )
         )))
         .thenRun((s: State) => addRoomActivity(RoomActivity(
           s.airingId,
           userContext.user.userId.get,
           RoomActivityType.Joined
        )))
        case JoinRoom(userContext, _) if (
          status.status == RoomStatusType.PostGame ||
          status.status == RoomStatusType.Closed
        ) => Effect
          .none
          .thenRun((_: State) => userExtension.roomMessage(
            userContext.user.userId.get,
            RoomClosed
          ))
          .thenStop()
        case LeaveRoom(roomId, userId) => Effect.persist(LeftRoom(
          roomId,
          userId
        )).thenRun((s: State) => for {
          room <- prevState.hashes(roomId.name).getRoom(roomId)
          participant <- room.getParticipant(userId)
          currentRoom <- s.hashes(roomId.name).getRoom(roomId)
        } yield {
          currentRoom.participants.foreach(p => userExtension.roomMessage(
            p.userId,
            ParticipantLeft(participant)
          ))
        }).thenRun((s: State) => addRoomActivity(RoomActivity(
          s.airingId,
          userId,
          RoomActivityType.Left
        )))
        case SendMessage(roomId, userId, message) if !LinkScanner.hasLinks(message) =>
          (for {
            hashRoom <- prevState.hashes.get(roomId.name)
            roomMessage <- hashRoom.createTextMessage(
              roomId,
              userId,
              message,
            )
          } yield Effect.persist(MessageReceived(roomId, roomMessage))
            .thenRun((s: State) => s.hashes(roomId.name).broadcastMessage(roomId, roomMessage))
            .thenRun((_: State) => metrics.incMessages()))
            .getOrElse(Effect.none)
        case SendMessage(roomId, userId, message) if LinkScanner.hasLinks(message) =>
          (for {
            hashRoom <- prevState.hashes.get(roomId.name)
            roomMessage <- hashRoom.createTextMessage(
              roomId,
              userId,
              message,
            )
          } yield Effect.none
            .thenRun((s: State) => singletonExtension.linkScanner ! ScanMessage(
              s.airingId,
              roomId,
              roomMessage
          ))).getOrElse(Effect.none)
        case SendGif(roomId, userId, url) if LinkScanner.hasLinks(url) =>
          (for {
            hashRoom <- prevState.hashes.get(roomId.name)
            gifMessage <- hashRoom.createTenorMessage(
              roomId,
              userId,
              url
            )
          } yield Effect.none
            .thenRun((s: State) => s.hashes(roomId.name).broadcastMessage(roomId, gifMessage))
            .thenRun((_: State) => metrics.incMessages()))
            .getOrElse(Effect.none)
        case SendTextMessageWithLinks(roomId, message) =>
          Effect
            .persist(MessageReceived(roomId, message))
            .thenRun((s: State) => s.hashes(roomId.name).broadcastMessage(roomId, message))
            .thenRun((_: State) => metrics.incMessages())
        case AddReaction(roomId, userId, messageId, shortCode) => (for {
          hashRoom <- prevState.hashes.get(roomId.name)
          roomMessage <- hashRoom.addReaction(
            roomId,
            messageId,
            userId,
            shortCode
          )
        } yield Effect.persist(AddReactionReceived(roomId, roomMessage))
          .thenRun((s: State) => s.hashes(roomId.name).broadcastUpdateMessage(roomId, roomMessage))
          .thenRun((_: State) => userExtension.roomMessage(
            userId,
            ReactionAdded
          ))
          .thenRun((_: State) => metrics.incReaction()))
          .getOrElse(Effect.none)
        case RemoveReaction(roomId, userId, messageId, shortCode) => (for {
          hashRoom <- prevState.hashes.get(roomId.name)
          roomMessage <- hashRoom.removeReaction(
            roomId,
            messageId,
            userId,
            shortCode
          )
        } yield Effect.persist(RemoveReactionReceived(roomId, roomMessage))
          .thenRun((s: State) => s.hashes(roomId.name).broadcastUpdateMessage(roomId, roomMessage))
          .thenRun((_: State) => userExtension.roomMessage(
            userId,
            ReactionRemoved
          )))
          .getOrElse(Effect.none)
        case _ => Effect.unhandled
      }
    }

  private[this] def eventHandler(
    implicit
    ctx: ActorContext[Command]
  ): (State, Event) => State =
    (state, event) => event match {
      case JoinedRoom(participant, hash) =>
        state.hashes.get(hash).fold(
          state.copy(
            hashes = state.hashes + (
              hash -> HashRoom(hash).addParticipant(participant)
            )
          )
        )(hashRoom => state.copy(
          hashes = state.hashes.updated(hashRoom.name, hashRoom.addParticipant(
            participant
          ))
        ))

      case LeftRoom(roomId, userId) =>
        state.copy(
          hashes = state.hashes.updated(roomId.name, state.hashes(roomId.name).removeParticipant(
            roomId, userId
          ))
        )
      case SetAiringStatus(status) =>
        state.copy(status = Some(status))
      case ClearAiring =>
        state.copy(status = Option.empty)
      case MessageReceived(roomId, roomMessage) =>
        state.copy(
          hashes = state.hashes.updated(
            roomId.name,
            state.hashes(roomId.name).addMessage(roomId, roomMessage).get
          )
        )
      case AddReactionReceived(roomId, roomMessage) =>
        state.copy(
          hashes = state.hashes.updated(
            roomId.name,
            state.hashes(roomId.name).updateMessage(roomId, roomMessage).get
          )
        )
      case RemoveReactionReceived(roomId, roomMessage) =>
        state.copy(
          hashes = state.hashes.updated(
            roomId.name,
            state.hashes(roomId.name).updateMessage(roomId, roomMessage).get
          )
        )
    }
}
