package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.collection.immutable.{ListSet, SortedSet}

object Chatroom {
  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Chatroom")

  sealed trait Command

  final case class JoinRoom(
    userId: UUID,
    username: String,
    actorRef: ActorRef[Command]
  ) extends Command
  final case class LeaveRoom(
    roomId: UUID,
    actorRef: ActorRef[Command]
  ) extends Command
  final case class SendMessage(
    roomId: UUID,
    actorRef: ActorRef[Command]
  ) extends Command

  final case class RoomJoined(
    roomId: UUID
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

  private sealed trait Event

  private final case class JoinedRoom(
    userId: UUID,
    username: String,
    actorRef: ActorRef[Command]
  ) extends Event
  private final case class LeftRoom(
    roomId: UUID,
    actorRef: ActorRef[Command]
  ) extends Event

  final case class State(rooms: SortedSet[Room])

  def apply(airingId: UUID, persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.debug(s"Starting room for airing ${airingId.toString}")

    EventSourcedBehavior(
      persistenceId,
      State(SortedSet(Room())),
      commandHandler,
      eventHandler
    )
  }

  private[this] val commandHandler: (State, Command) => Effect[Event, State] =
    (state, command) => command match {
      case JoinRoom(userId, username, actorRef) => Effect.persist(JoinedRoom(
        userId, username, actorRef
      )).thenRun((s: State) => actorRef ! RoomJoined(s.rooms.head.roomId))
        .thenRun((s: State) => actorRef ! RoomParticipants(s.rooms.head.participants.tail))
        .thenRun((s: State) => s.rooms.head.participants.tail.foreach(_.actorRef ! ParticipantJoined(
          s.rooms.head.participants.head
        )))
      case LeaveRoom(roomId, actorRef) => Effect.persist(LeftRoom(
        roomId, actorRef
      ))
    }

  private[this] val eventHandler: (State, Event) => State =
    (state, event) => event match {
      case JoinedRoom(userId, username, actorRef) =>
        if (state.rooms.head.isFull) {
          state.copy(
            rooms = state.rooms + Room(
              participants = ListSet(RoomParticipant(
                userId,
                username,
                actorRef
              ))
            )
          )
        } else {
          state.copy(
            rooms = state.rooms.tail + state.rooms.head.add(RoomParticipant(
              userId,
              username,
              actorRef
            ))
          )
        }

      case LeftRoom(roomId, actorRef) =>
        state.copy(
          rooms = state
            .rooms
            .filterNot(_.roomId == roomId) +
            state
              .rooms
              .find(_.roomId == roomId)
              .get
              .remove(actorRef)
        )

    }
}
