package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object Chatroom {
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
  final case class RoomParticipants(
    participants: Set[RoomParticipant]
  ) extends Command
  final case class ParticipantJoined(
    participant: RoomParticipant
  ) extends Command
  final case class ParticipantLeft(
    participant: RoomParticipant
  ) extends Command
  final case class MessageSent(
    participant: RoomParticipant,
    message: String
  ) extends Command

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

  final case class State(
    airingId: UUID,
    rooms: Map[String, NamedRoom]
  )

  def apply(airingId: UUID, persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.debug(s"Starting room for airing ${airingId.toString}")

    EventSourcedBehavior(
      persistenceId,
      State(
        airingId,
        Map(
          "general" -> NamedRoom("general")
        )
      ),
      commandHandler(ctx),
      eventHandler
    )
  }

  private[this] def commandHandler: ActorContext[Command] => (State, Command) => Effect[Event, State] =
    (ctx: ActorContext[Command]) => (_, command) => command match {
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
      )).thenRun((s: State) => s
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

    }
}
