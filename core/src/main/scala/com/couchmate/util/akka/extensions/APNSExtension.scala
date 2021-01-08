package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.services.notification.{NotificationFailure, NotificationSuccess}
import com.malliina.push.apns._
import com.typesafe.config.ConfigFactory
import play.api.libs.json.{JsString, JsValue}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class APNSExtension(system: ActorSystem[_]) extends Extension {
  private[this] val config = ConfigFactory.load()

  private[this] val apns = APNSTokenConf(
    Source.fromResource("apns.p8"),
    KeyId(config.getString("apple.key")),
    TeamId(config.getString("apple.team"))
  )

  private[this] val client = APNSTokenClient(
    apns,
    isSandbox = config.getString("environment") != "production"
  )

  private[this] val topic = APNSTopic(
    config.getString("apple.topic")
  )

  def sendNotification(
    notificationId: UUID,
    token: String,
    title: String,
    body: Option[String] = None,
    data: Map[String, JsString] = Map()
  )(implicit ec: ExecutionContext): Future[Either[NotificationFailure, NotificationSuccess]] =
    client.push(
      APNSToken.build(token).get,
      APNSRequest.withTopic(
        topic,
        APNSMessage(
          APSPayload.full(
            AlertPayload(title, body),
            badge = Some(0),
            sound = Some("default")
          )
        ).copy(data = data)
      )
    ) map {
      case Left(value) => Left(NotificationFailure(notificationId, Some(value.reason), Some(value.description)))
      case Right(_) => Right(NotificationSuccess(notificationId))
    }

}

object APNSExtension extends ExtensionId[APNSExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): APNSExtension = new APNSExtension(system)
}