package com.couchmate.services.notification

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.couchmate.common.dao.UserNotificationConfigurationDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ApplicationPlatform
import com.couchmate.util.akka.extensions.{APNSExtension, DatabaseExtension}
import com.malliina.push.apns.{APNSError, APNSIdentifier}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object NotificationCoordinator
  extends UserNotificationConfigurationDAO {
  sealed trait Command

  private final case object Run extends Command

  private final case class Send(tokens: Seq[String]) extends Command
  private final case class SendError(ex: Throwable) extends Command

  private final case class SendResults(results: Seq[Either[APNSError, APNSIdentifier]]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db
    val apns: APNSExtension = APNSExtension(ctx.system)

    ctx.log.info(s"Starting NotificationCoordinator")

    Behaviors.withTimers { timers =>
//      timers.startTimerAtFixedRate(
//        Run,
//        10 seconds
//      )

      Behaviors.receiveMessage {
        case Run =>
          ctx.log.info(s"Running notifications")
          ctx.pipeToSelf(getActiveUserNotificationConfigurationsForPlatform(
            ApplicationPlatform.iOS
          )) {
            case Success(value) => Send(value.flatMap(_.token))
            case Failure(exception) => SendError(exception)
          }
        Behaviors.same
        case Send(tokens) => ctx.pipeToSelf(
          Future.sequence(tokens.map(apns.sendNotification(
            _, "Show starting soon", Some("Click to join others")
          )))
        ) {
          case Success(value) => SendResults(value)
          case Failure(exception) => SendError(exception)
        }
        Behaviors.same
        case SendResults(results) =>
          ctx.log.info(s"Got ${results.size} results back from notifications")
          results.foreach {
            case Left(value) =>
              ctx.log.info(s"Notification error: ${value.reason}")
            case Right(value) =>
              ctx.log.info(s"Notification successful: ${value.id}")
          }
        Behaviors.same
        case SendError(ex) =>
          ctx.log.error(s"Got APNS send error", ex)
          Behaviors.same
      }
    }
  }
}
