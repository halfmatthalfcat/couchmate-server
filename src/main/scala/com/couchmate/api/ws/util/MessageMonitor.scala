package com.couchmate.api.ws.util

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.api.ws.{RoomContext, SessionContext}
import com.couchmate.data.models.UserRole
import com.couchmate.services.room.Chatroom
import com.couchmate.util.akka.extensions.RoomExtension
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

object MessageMonitor {
  sealed trait Command

  case class ReceiveMessage(message: String) extends Command

  case class LockSending(duration: Int) extends Command
  case object UnlockSending extends Command

  case object Complete extends Command

  private final case object DecThrottle extends Command

  private final case object ThrottleKey

  def apply(
    session: SessionContext,
    room: RoomContext,
    senderRef: ActorRef[Command],
    chatAdapterRef: ActorRef[Chatroom.Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val config: Config = ConfigFactory.load()
    val lobby: RoomExtension =
      RoomExtension(ctx.system)

    def accepting: Behavior[Command] = Behaviors.receiveMessagePartial {
      case ReceiveMessage(message) => lobby.message(
          room.airingId,
          room.roomId,
          session.user.userId.get,
          message
        )
        throttling
      case Complete => Behaviors.stopped
    }

    def throttling: Behavior[Command] = Behaviors.withTimers { timers =>
      def run(remaining: Int): Behavior[Command] = Behaviors.receiveMessage {
        case DecThrottle if remaining > 1 =>
          senderRef ! LockSending(remaining - 1)
          run(remaining - 1)
        case DecThrottle =>
          senderRef ! UnlockSending
          timers.cancel(ThrottleKey)
          accepting
      }

      if (session.user.role == UserRole.Anon) {
        timers.startTimerAtFixedRate(
          ThrottleKey,
          DecThrottle,
          1 second
        )

        run(FiniteDuration(
          config.getDuration("features.anon.throttle", SECONDS),
          SECONDS
        ).toSeconds.toInt)
      } else { accepting }
    }

    accepting
  }
}