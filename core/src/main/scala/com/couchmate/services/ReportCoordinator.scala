package com.couchmate.services

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import com.couchmate.common.dao.UserActivityAnalyticsDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserActivityAnalytics
import com.couchmate.util.akka.extensions.{DatabaseExtension, MailExtension}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.javaapi.CollectionConverters

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ReportCoordinator extends UserActivityAnalyticsDAO {
  sealed trait Command

  private final case object RunUserActivityAnalyticsReport extends Command

  private final case class UserActivityAnalyticsReportSuccess(report: UserActivityAnalytics) extends Command
  private final case class UserActivityAnalyticsReportFailed(ex: Throwable) extends Command

  private final case class UserActivityAnalyticsReportsSent(results: Seq[Boolean]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info(s"ReportCoordinator started")
    val config: Config = ConfigFactory.load()

    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db
    implicit val timeout: Timeout = 5 seconds

    val mailer: MailExtension = MailExtension(ctx.system)

    val scheduler: QuartzSchedulerExtension =
      QuartzSchedulerExtension(ctx.system.toClassic)

    scheduler.schedule(
      "EveryDay",
      ctx.self.toClassic,
      RunUserActivityAnalyticsReport,
      None
    )

    Behaviors.receiveMessage {
      case RunUserActivityAnalyticsReport => ctx.pipeToSelf(getAndAddUserAnalytics) {
        case Success(report) => UserActivityAnalyticsReportSuccess(report)
        case Failure(exception) => UserActivityAnalyticsReportFailed(exception)
      }
        Behaviors.same
      case UserActivityAnalyticsReportSuccess(report) => ctx.pipeToSelf(Future.sequence(
        CollectionConverters.asScala(config.getStringList("reports.userAnalytics.recipients")).map(
          email => mailer.analyticsReport(email, report)
        )
      )) {
        case Success(value) => UserActivityAnalyticsReportsSent(value.toSeq)
        case Failure(_) => UserActivityAnalyticsReportsSent(Seq.empty)
      }
        Behaviors.same
      case UserActivityAnalyticsReportFailed(ex) =>
        ctx.log.error(s"Failed to get Analytics Report: ${ex.getMessage}")
        Behaviors.same
      case UserActivityAnalyticsReportsSent(sent) =>
        ctx.log.info(s"Sent ${sent.size} analytics reports")
        Behaviors.same
    }
  }
}
