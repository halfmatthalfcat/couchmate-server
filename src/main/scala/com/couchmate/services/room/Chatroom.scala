package com.couchmate.services.room

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.couchmate.data.models.AiringStatus
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.AiringDAO
import com.couchmate.data.models.RoomStatusType.Closed
import com.couchmate.util.akka.extensions.DatabaseExtension

import scala.concurrent.ExecutionContext
import scala.compat.java8.DurationConverters
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Chatroom extends AiringDAO {
  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Chatroom")

  sealed trait Command

  final case class JoinRoom(
    userId: UUID,
    username: String,
    actorRef: ActorRef[Command],
    roomName: String = "general"
  ) extends Command
  final case class LeaveRoom(
    roomId: RoomId,
    participant: RoomParticipant
  ) extends Command
  final case class SendMessage(
    roomId: RoomId,
    userId: UUID,
    message: String
  ) extends Command

  final case class RoomJoined(
    airingId: UUID,
    roomId: RoomId
  ) extends Command
  final case class RoomRejoined(
    airingId: UUID,
    roomId: RoomId
  ) extends Command
  final case class RoomEnded(
    airingId: UUID,
    roomId: RoomId
  ) extends Command
  final case class RoomParticipants(
    participants: Set[RoomParticipant]
  ) extends Command
  final case class ParticipantJoined(
    participant: RoomParticipant
  ) extends Command
  final case class ParticipantLeft(
    participant: RoomParticipant
  ) extends Command
  final case class ParticipantKicked(
    participant: RoomParticipant
  ) extends Command
  final case class MessageSent(
    participant: RoomParticipant,
    message: String
  ) extends Command

  private final case class GetAiringSuccess(airing: AiringStatus) extends Command
  private final case class GetAiringFailure(err: Throwable) extends Command

  private final case object RoomEnding extends Command

  private sealed trait Event

  private final case class JoinedRoom(
    userId: UUID,
    username: String,
    actorRef: ActorRef[Command],
    roomName: String
  ) extends Event
  private final case class LeftRoom(
    roomId: RoomId,
    participant: RoomParticipant
  ) extends Event
  private final case class SetAiringStatus(
    status: AiringStatus
  ) extends Event

  final case class State(
    airingId: UUID,
    status: Option[AiringStatus],
    rooms: Map[String, NamedRoom]
  )

  def apply(airingId: UUID, persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.pipeToSelf(getAiringStatus(airingId)) {
      case Success(Some(value)) => GetAiringSuccess(value)
      case Success(None) => GetAiringFailure(new RuntimeException(s"Unable to get Airing for ${airingId}"))
      case Failure(ex) => GetAiringFailure(ex)
    }

    Behaviors.withTimers { timers =>
      EventSourcedBehavior(
        persistenceId,
        State(
          airingId,
          None,
          Map(
            "general" -> NamedRoom("general")
          )
        ),
        commandHandler(ctx, timers),
        eventHandler
      )
    }
  }

  private[this] def commandHandler: (ActorContext[Command], TimerScheduler[Command]) => (State, Command) => Effect[Event, State] =
    (ctx: ActorContext[Command], timers: TimerScheduler[Command]) => (prevState, command) => prevState match {
      case State(_, None, _) => command match {
        case GetAiringSuccess(AiringStatus(_, _, _, _, _, Closed)) => Effect.stop()
        case GetAiringSuccess(status) => Effect
          .persist(SetAiringStatus(status))
          .thenRun((s: State) =>
            timers.startSingleTimer(
              RoomEnding,
              DurationConverters.toScala(Duration.between(
                LocalDateTime.now(ZoneId.of("UTC")),
                s.status.get.endTime,
              )).max(FiniteDuration(0L, SECONDS))
            )
          )
          .thenUnstashAll()
        case GetAiringFailure(_) => Effect.stop()
        case _ => Effect.stash()
      }
      case _ => command match {
        case RoomEnding => Effect
          .stop()
          .thenRun((s: State) => {
            s.rooms.foreachEntry {
              case (_, namedRoom) => namedRoom.rooms.foreach { room =>
                room.participants.foreach { participant =>
                  participant.actorRef ! RoomEnded(
                    s.airingId,
                    room.roomId
                  )
                }
              }
            }
          })
        case JoinRoom(userId, username, actorRef, roomName) => Effect.persist(JoinedRoom(
          userId, username, actorRef, roomName
        )).thenRun((s: State) => actorRef ! RoomJoined(s.airingId, s.rooms(roomName).getParticipantRoom(userId).get.roomId))
         .thenRun((s: State) => actorRef ! RoomParticipants(s.rooms(roomName).getParticipantRoom(userId).get.participants.toSet))
         .thenRun((s: State) => s.rooms(roomName).rooms.head.participants.tail.foreach(_.actorRef ! ParticipantJoined(
           s.rooms(roomName).rooms.head.participants.head
         )))
         .thenRun((s: State) => ctx.watchWith(
           s.rooms(roomName).rooms.head.participants.head.actorRef,
           LeaveRoom(
             s.rooms(roomName).rooms.head.roomId,
             s.rooms(roomName).rooms.head.participants.head,
           )
         ))
        case LeaveRoom(roomId, participant) => Effect.persist(LeftRoom(
          roomId,
          participant
        )).thenRun((s: State) => prevState
          .rooms(roomId.name)
          .getParticipantRoom(participant.userId)
          .get
          .participants
          .foreach(_.actorRef ! ParticipantLeft(participant))
        )
        case SendMessage(roomId, userId, message) => Effect
          .none
          .thenRun((s: State) => s
            .rooms(roomId.name)
            .rooms
            .find(_.roomId == roomId)
            .fold(()) { room =>
              val sender: Option[RoomParticipant] =
                room.getParticipant(userId)

              if (sender.nonEmpty) {
                room
                  .participants
                  .map(_.actorRef)
                  .foreach(_ ! MessageSent(
                    sender.get,
                    message,
                  ))
              }
            }
          )
        case _ => Effect.unhandled
      }
    }

  private[this] val eventHandler: (State, Event) => State =
    (state, event) => event match {
      case JoinedRoom(userId, username, actorRef, roomName) =>
        state.rooms.get(roomName).fold(
          state.copy(
            rooms = state.rooms + (
              roomName -> NamedRoom(roomName).addParticipant(
                userId, username, actorRef
              )
            )
          )
        )(room => state.copy(
          rooms = state.rooms.updated(room.name, room.copy(
            rooms = room.rooms.tail + room.rooms.head.add(RoomParticipant(
              userId, username, actorRef
            ))
          ))
        ))

      case LeftRoom(roomId, participant) =>
        state.copy(
          rooms = state.rooms.updated(roomId.name, state.rooms(roomId.name).removeParticipant(
            roomId, participant
          ))
        )
      case SetAiringStatus(status) =>
        state.copy(status = Some(status))
    }
}
