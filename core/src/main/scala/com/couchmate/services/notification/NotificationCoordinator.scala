package com.couchmate.services.notification

import java.time.{LocalDateTime, ZoneId}
import java.time.temporal.ChronoUnit

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import com.couchmate.common.dao.UserNotificationQueueDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationQueueItem}
import com.couchmate.common.util.DateUtils
import com.couchmate.util.akka.extensions.{APNSExtension, DatabaseExtension, FCMExtension}
import play.api.libs.json.JsString

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object NotificationCoordinator
  extends UserNotificationQueueDAO {
  sealed trait Command

  private final case object Run extends Command

  private final case class Send(items: Seq[UserNotificationQueueItem]) extends Command
  private final case class SendError(ex: Throwable) extends Command

  private final case class SendResults(
    results: Seq[Either[NotificationFailure, NotificationSuccess]]
  ) extends Command

  private sealed trait Event

  private final case object IncMinute extends Event

  private final case class State(
    lastMinute: LocalDateTime,
    currentMinute: LocalDateTime
  )

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db
    val apns: APNSExtension = APNSExtension(ctx.system)
    val google: FCMExtension = FCMExtension(ctx.system)

    ctx.log.info(s"Starting NotificationCoordinator")

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(
        Run,
        1 minute
      )

      def commandHandler: (State, Command) => Effect[Event, State] =
        (_, command) => command match {
          case Run =>
            Effect.persist(IncMinute).thenRun(state => {
              ctx.log.info(s"Running notifications for ${state.currentMinute.toString}")
              ctx.pipeToSelf(getUserNotificationItems(
                state.lastMinute,
                state.currentMinute
              )) {
                case Success(items) => Send(items)
                case Failure(exception) => SendError(exception)
              }
            })
          case Send(items) => Effect.none.thenRun(_ => {
            ctx.pipeToSelf(
              Future.sequence(items.collect({
                case UserNotificationQueueItem(notificationId, _, airingId, _, hash, title, callsign, ApplicationPlatform.iOS, Some(token), _, _, _, _, _) => apns.sendNotification(
                  notificationId,
                  token,
                  "This show's room is now open! Click to join the conversation on Couchmate.",
                  Some(s"$title${callsign.map(cs => s" on $cs").getOrElse("")}"),
                  Map(
                    "notificationId" -> JsString(notificationId.toString),
                    "airingId" -> JsString(airingId),
                    "hash" -> JsString(hash)
                  )
                )
                case UserNotificationQueueItem(notificationId, _, airingId, _, hash, title, callsign, ApplicationPlatform.Android, Some(token), _, _, _, _, _) => google.sendNotification(
                  notificationId,
                  token,
                  s"$title${callsign.map(cs => s" on $cs").getOrElse("")}",
                  Some("This show's room is now open! Click to join the conversation on Couchmate."),
                  Map(
                    "notificationId" -> notificationId.toString,
                    "airingId" -> airingId,
                    "hash" -> hash
                  )
                )
              })),
            ) {
              case Success(value) => SendResults(value)
              case Failure(exception) => SendError(exception)
            }
          })
          case SendResults(results) => Effect.none.thenRun(_ => {
            Future.sequence(results.collect({
              case Right(NotificationSuccess(notificationId)) =>
                deliverUserNotificationItem(notificationId, true)
              case Left(NotificationFailure(notificationId, cause, description)) =>
                ctx.log.error(s"Failed to deliver notification: ${cause}:${description}")
                deliverUserNotificationItem(notificationId, false)
            }))
          })
          case SendError(ex) => Effect.none.thenRun(_ => {
            ctx.log.error(s"Got APNS send error", ex)
          })
        }

      def eventHandler: (State, Event) => State =
        (state, event) => event match {
          case IncMinute => state.copy(
            lastMinute = state.currentMinute,
            currentMinute = DateUtils.roundNearestMinute(LocalDateTime.now(ZoneId.of("UTC")))
          )
        }

      val initDate: LocalDateTime =
        DateUtils.roundNearestMinute(LocalDateTime.now(ZoneId.of("UTC")))

      EventSourcedBehavior(
        PersistenceId.ofUniqueId("NotificationCoordinator"),
        State(
          initDate,
          initDate
        ),
        commandHandler,
        eventHandler
      ).withRetention(
        RetentionCriteria.snapshotEvery(
          numberOfEvents = 50, keepNSnapshots = 2
        ).withDeleteEventsOnSnapshot
      )
    }
  }

  private[this] def getUserNotificationItems(
    from: LocalDateTime,
    to: LocalDateTime,
  )(implicit db: Database): Future[Seq[UserNotificationQueueItem]] = {
    val diff: Long = ChronoUnit.MINUTES.between(
      from,
      to
    )
    if (diff > 1 && diff > 15) {
      getUserNotificationItemsForDeliveryRange(
        to.minusMinutes(15),
        to
      )
    } else if (diff > 1) {
      getUserNotificationItemsForDeliveryRange(
        from,
        to
      )
    } else {
      getUserNotificationItemsForDelivery(to)
    }
  }
}
