package com.couchmate.services.notification

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import com.couchmate.common.dao.UserNotificationQueueDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationQueueItem}
import com.couchmate.common.util.DateUtils
import com.couchmate.util.akka.extensions.{APNSExtension, DatabaseExtension}
import com.malliina.push.apns.{APNSError, APNSIdentifier}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object NotificationCoordinator
  extends UserNotificationQueueDAO {
  sealed trait Command

  private final case object Run extends Command

  private final case class Send(items: Seq[UserNotificationQueueItem]) extends Command
  private final case class SendError(ex: Throwable) extends Command

  private final case class SendResults(results: Seq[Either[APNSError, APNSIdentifier]]) extends Command

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

    ctx.log.info(s"Starting NotificationCoordinator")

    Behaviors.withTimers { timers =>
//      timers.startTimerAtFixedRate(
//        Run,
//        1 minute
//      )

      def commandHandler: (State, Command) => Effect[Event, State] =
        (_, command) => command match {
          case Run =>
            Effect.persist(IncMinute).thenRun(state => {
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
                case UserNotificationQueueItem(_, _, _, _, title, _, Some(token), _, _, _, _) => apns.sendNotification(
                  token, title, Some("Click to join the conversation!")
                )
              })),
            ) {
              case Success(value) => SendResults(value)
              case Failure(exception) => SendError(exception)
            }
          })
          case SendResults(results) => Effect.none.thenRun(_ => {
            ctx.log.info(s"Got ${results.size} results back from notifications")
            results.foreach {
              case Left(value) =>
                ctx.log.info(s"Notification error: ${value.reason}")
              case Right(value) =>
                ctx.log.info(s"Notification successful: ${value.id}")
            }

          })
          case SendError(ex) => Effect.none.thenRun(_ => {
            ctx.log.error(s"Got APNS send error", ex)
          })
        }

      def eventHandler: (State, Event) => State =
        (state, event) => event match {
          case IncMinute => state.copy(
            lastMinute = state.currentMinute,
            currentMinute = state.currentMinute.plusMinutes(1)
          )
        }

      val initDate: LocalDateTime =
        DateUtils.roundNearestMinute(LocalDateTime.now())

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
