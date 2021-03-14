package com.couchmate.util.akka.extensions

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.common.models.data.{RoomActivityAnalytics, UserActivityAnalytics}
import com.couchmate.util.mail.Fragments._
import com.couchmate.util.mail.{AnalyticsReport, MailgunCallback}
import com.typesafe.config.{Config, ConfigFactory}
import net.sargue.mailgun.{Configuration, Mail}
import scalatags.Text.all._

import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.concurrent.{Future, Promise}

class MailExtension(system: ActorSystem[_]) extends Extension {
  private[this] val config: Config = ConfigFactory.load()
  private[this] val hostname: String = config.getString("hostname")
  private[this] val apiKey: String = config.getString("mailgun.apiKey")

  private[this] val mailgunConfiguration: Configuration =
    new Configuration()
      .domain("mail.couchmate.com")
      .apiKey(apiKey)
      .from("Couchmate", "no-reply@couchmate.com")

  def accountRegistration(emailAddress: String, token: String): Future[Boolean] = {
    val p: Promise[Boolean] = Promise[Boolean]()
    Mail.using(mailgunConfiguration).to(emailAddress).subject("Couchmate Account Registration").html(email(
      banner("Couchmate Account Registration") ++ Seq(
        row(
          emailText("You're almost there!")
        ),
        row(
          emailText("Click the following "),
          emailLink("link", s"https://${hostname}/register?token=$token"),
          emailText(" to successfully register your account.")
        )
      ),
    ).toString).build().sendAsync(MailgunCallback(p))
    p.future
  }

  def forgotPassword(emailAddress: String, token: String): Future[Boolean] = {
    val p: Promise[Boolean] = Promise[Boolean]()
    Mail.using(mailgunConfiguration).to(emailAddress).subject("Couchmate Forgot Password").html(email(
      banner("Couchmate Forgot Password") ++ Seq(
        row(
          emailText("Ruh Roh! Let's get you back to chatting."),
        ),
        row(
          emailText("Click this "),
          emailLink("link", s"https://${hostname}/reset?token=$token"),
          emailText(" to reset your password.")
        )
      ),
    ).toString).build().sendAsync(MailgunCallback(p))
    p.future
  }

  def analyticsReport(
    emailAddress: String,
    userAnalyticsReport: UserActivityAnalytics,
    roomAnalyticsReport: RoomActivityAnalytics
  ): Future[Boolean] = {
    val p: Promise[Boolean] = Promise[Boolean]()
    Mail.using(mailgunConfiguration).to(emailAddress).subject(
      s"Couchmate Analytics Report - ${userAnalyticsReport.reportDate.toLocalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))}"
    ).html(email(
      banner(
        "Couchmate Analytics Report",
        Some(userAnalyticsReport.reportDate.toLocalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
      ) ++ Seq(
        row(h2("User Count")),
        row(AnalyticsReport.userCountTable(userAnalyticsReport)),
        row(h2("User Session")),
        row(AnalyticsReport.userSessionTable(userAnalyticsReport)),
        row(h2("Room Stats (24h)")),
        row(AnalyticsReport.roomActivityTable(roomAnalyticsReport.last24)),
        row(h2("Room Stats (7d)")),
        row(AnalyticsReport.roomActivityTable(roomAnalyticsReport.lastWeek)),
        row(h2("Room Stats (30d)")),
        row(AnalyticsReport.roomActivityTable(roomAnalyticsReport.lastMonth))
      ),
    ).toString).build().sendAsync(MailgunCallback(p))
    p.future
  }

}

object MailExtension extends ExtensionId[MailExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): MailExtension = new MailExtension(system)
}