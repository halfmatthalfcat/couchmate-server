//package com.couchmate.api.ws
//
//import java.time.{LocalDateTime, ZoneOffset}
//
//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
//import akka.http.scaladsl.model.sse.ServerSentEvent
//import akka.http.scaladsl.model.ws.{Message, TextMessage}
//import com.couchmate.api.models.WSMessage
//import com.couchmate.api.models.grid.Grid
//import com.couchmate.data.db.CMDatabase
//import com.couchmate.util.DateUtils
//import com.couchmate.util.stream.WSStream
//import com.typesafe.scalalogging.LazyLogging
//import play.api.libs.json.Json
//
//import scala.concurrent.ExecutionContext
//import scala.concurrent.duration._
//import scala.util.Success
//
//object GridHandler extends LazyLogging {
//  sealed trait Command
//
//  case class Connected(actorRef: ActorRef[Message]) extends Command
//  case object Ack extends Command
//  case object Stop extends Command
//
//  case object TimerKey extends Command
//  case object Pull extends Command
//  case class PushGrid(grid: Grid) extends Command
//
//  def apply(providerId: Long, page: Int): Behavior[Command] = Behaviors.setup { ctx =>
//    Behaviors.withTimers { timers =>
//      implicit val system: ActorSystem[Nothing] = ctx.system
//      implicit val ec: ExecutionContext = ctx.executionContext
//      val db: CMDatabase = CMDatabase()
//
//      timers.startTimerAtFixedRate(
//        TimerKey,
//        Pull,
//        5 second,
//      )
//
//      def run(socket: Option[ActorRef[Message]]): Behavior[Command] = Behaviors.receiveMessage {
//        case Connected(actorRef) =>
//          run(Some(actorRef))
//        case Pull => ctx.pipeToSelf(db.grid.getGrid(
//          providerId,
//          DateUtils.roundNearestHour(
//            LocalDateTime
//              .now(ZoneOffset.UTC)
//              .plusHours(page)
//          ),
//          60,
//          )) {
//          case Success(grid) => PushGrid(grid)
//        }
//          Behaviors.same
//        case PushGrid(grid) =>
//          socket.fold(()) { resolvedSocket =>
//            resolvedSocket ! WSMessage("grid", Some(grid))
//          }
//          Behaviors.same
//        case Stop =>
//          db.close()
//          Behaviors.stopped
//      }
//
//      run(None)
//    }
//  }
//
//  def ws(
//    providerId: Long,
//    page: Int,
//  ): Behavior[WSStream.Command] = WSStream.interceptor(apply(providerId, page)) {
//    case WSStream.Connected(actorRef) => Connected(actorRef)
//    case WSStream.Complete => Stop
//  }
//}
