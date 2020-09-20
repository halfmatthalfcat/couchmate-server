package com.couchmate.api.ws.util

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.api.ws.Commands
import com.couchmate.api.ws.Commands.Outgoing
import com.couchmate.api.ws.protocol.Ping

import scala.concurrent.duration._

case class State(
  latencies: List[Long],
  deathSaves: Int,
  lastPing: Option[LocalDateTime],
  status: ConnectionStatus
)

object ConnectionMonitor {
  sealed trait Command

  case object ReceivePong extends Command

  private final case object SendPing extends Command
  private final case object PoisonPill extends Command

  def apply(ws: ActorRef[Commands.Command], parent: ActorRef[Commands.Command]): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendPing, 5 seconds)

      ctx.watchWith(parent, PoisonPill)

      ws ! Outgoing(Ping)

      def run(state: State): Behavior[Command] = Behaviors.receiveMessage {
        case SendPing =>
          state.lastPing.fold({
            ws ! Outgoing(Ping)
            run(state.copy(
              lastPing = Some(LocalDateTime.now(ZoneId.of("UTC"))),
            ))
          }) { _ =>
            ws ! Outgoing(Ping)
            if (state.deathSaves == 12) {
              ctx.log.debug(s"Connection monitor sending complete")
              parent ! Commands.Complete
              Behaviors.stopped
            } else if (state.deathSaves == 6) {
              run(state.copy(
                status = ConnectionStatus.Lost,
                deathSaves = state.deathSaves + 1
              ))
            } else {
              run(state.copy(
                deathSaves = state.deathSaves + 1
              ))
            }
          }
        case ReceivePong =>
          state.lastPing.fold(Behaviors.same[Command]) { lastPing =>
            val latency: Long = lastPing.until(
              LocalDateTime
                .now(ZoneId.of("UTC")),
              ChronoUnit.MILLIS
            )
            val latencies: List[Long] = latency :: state.latencies.take(4)
            val averageLatency: Long = latencies.sum / latencies.size
            if (averageLatency <= 350) {
              run(state.copy(
                latencies = latencies,
                status = ConnectionStatus.Good,
                lastPing = Option.empty,
                deathSaves = 0
              ))
            } else if (averageLatency <= 1000) {
              run(state.copy(
                latencies = latencies,
                status = ConnectionStatus.Degraded,
                lastPing = Option.empty,
                deathSaves = 0
              ))
            } else {
              run(state.copy(
                latencies = latencies,
                status = ConnectionStatus.Weak,
                lastPing = Option.empty,
                deathSaves = 0
              ))
            }
          }
        case PoisonPill => Behaviors.stopped
      }

      run(State(
        List.empty,
        0,
        Option.empty,
        ConnectionStatus.Unknown
      ))
    }
  }
}
