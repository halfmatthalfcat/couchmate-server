package com.couchmate.api

/**
 * WS Protocol
 */

import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.api.ws.protocol.Protocol
import com.couchmate.services.room.{RoomId, RoomMessage, RoomParticipant}

package object ws {
  object Commands {
    sealed trait Command
    type PartialCommand = PartialFunction[Command, Behavior[Command]]

    case class SocketConnected(actorRef: ActorRef[Command]) extends Command

    /**
     * Common Commands
     */

    case class Incoming(message: Protocol) extends Command
    case class Outgoing(message: Protocol) extends Command

    /**
     * Internal Commands
     */

    case object Closed                      extends Command
    case object Complete                    extends Command
    case class  ConnFailure(ex: Throwable)  extends Command
    case class  Failed(ex: Throwable)       extends Command

    object Connected {
      case class CreateNewSessionSuccess(
        session: SessionContext,
        geo: GeoContext,
        device: DeviceContext,
      ) extends Command
      case class CreateNewSessionFailure(err: Throwable) extends Command

      case class RestoreSessionSuccess(
        session: SessionContext,
        geo: GeoContext,
        device: DeviceContext,
      ) extends Command
      case class RestoreSessionFailure(err: Throwable) extends Command

      case class RestoreRoomSessionSuccess(
        session: SessionContext,
        geo: GeoContext,
        device: DeviceContext,
        airingId: String
      ) extends Command
      case class RestoreRoomSessionFailure(ex: Throwable) extends Command

      case object LogoutSuccess extends Command
      case class LogoutFailure(ex: Throwable) extends Command

      case class EmailValidated(exists: Boolean, valid: Boolean) extends Command
      case class EmailValidatedFailed(ex: Throwable) extends Command

      case class UsernameValidated(exists: Boolean, valid: Boolean) extends Command
      case class UsernameValidatedFailed(ex: Throwable) extends Command

      case class UsernameUpdated(session: SessionContext) extends Command
      case class UsernameUpdatedFailed(ex: Throwable) extends Command

      case object AccountRegistrationSent extends Command
      case class AccountRegistrationSentFailed(ex: Throwable) extends Command

      case class AccountVerified(session: SessionContext) extends Command
      case class AccountVerifiedFailed(ex: Throwable) extends Command

      case class LoggedIn(session: SessionContext) extends Command
      case class LoggedInFailed(ex: Throwable) extends Command

      case object ForgotPasswordSent extends Command
      case class ForgotPasswordSentFailed(ex: Throwable) extends Command

      case object ForgotPasswordComplete extends Command
      case class ForgotPasswordFailed(ex: Throwable) extends Command

      case object PasswordResetComplete extends Command
      case class PasswordResetFailed(ex: Throwable) extends Command

      case class ParticipantUnmuted(session: SessionContext) extends Command
      case class ParticipantUnmutedFailed(ex: Throwable) extends Command

      case class WordBlocked(session: SessionContext) extends Command
      case class WordBlockFailed(ex: Throwable)       extends Command

      case class WordUnblocked(session: SessionContext) extends Command
      case class WordUnblockFailed(ex: Throwable)       extends Command

      case class ProviderUpdated(
        providerId: Long,
        providerName: String,
        pulling: Boolean
      ) extends Command
      case class ProviderUpdatedFailed(ex: Throwable) extends Command

      case class UpdateGrid(grid: Grid) extends Command
    }

    object InRoom {
      case class RoomJoined(airingId: String, roomId: RoomId)           extends Command
      case class RoomRejoined(airingId: String, roomId: RoomId)         extends Command
      case class RoomEnded(airingId: String, roomId: RoomId)            extends Command
      case class SetParticipants(participants: Set[RoomParticipant])  extends Command
      case class AddParticipant(participant: RoomParticipant)         extends Command
      case class RemoveParticipant(participant: RoomParticipant)      extends Command
      case class MessageReplay(messages: List[RoomMessage])           extends Command

      case class ParticipantMuted(session: SessionContext)            extends Command
      case class ParticipantMutedFailed(ex: Throwable)                extends Command

      case object ParticipantReported                                 extends Command
      case class ParticipantReportFailed(ex: Throwable)               extends Command
    }

    object Messaging {
      case class OutgoingRoomMessage(
        message: RoomMessage
      ) extends Command

      case object MessageQueued       extends Command
      case object MessageThrottled    extends Command
      case object MessageQueueClosed  extends Command
      case class  MessageQueueFailed(
        ex: Throwable
      ) extends Command
    }
  }
}
