package com.couchmate.util.akka.extensions

import java.util.UUID

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.services.notification.{NotificationFailure, NotificationSuccess}
import com.malliina.push.fcm.FCMLegacyClient
import com.malliina.push.gcm.{GCMMessage, GCMNotification, GCMResult, GCMToken}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}

class FCMExtension(system: ActorSystem[_]) extends Extension {
  private[this] val config = ConfigFactory.load()

  private[this] val client = FCMLegacyClient(
    config.getString("google.fcmApiKey")
  )

  def sendNotification(
    notificationId: UUID,
    token: String,
    title: String,
    body: Option[String] = None,
    data: Map[String, String] = Map()
  )(implicit ec: ExecutionContext): Future[Either[NotificationFailure, NotificationSuccess]] =
    client.push(
      GCMToken(token),
      GCMMessage(
        notification = Some(GCMNotification(
          title = Some(title),
          body = body
        )),
        data = data
      )
    ).map(_.response.results.headOption match {
      case Some(GCMResult(_, _, Some(error))) => Left(NotificationFailure(
        notificationId,
        Some(error.name),
        None
      ))
      case Some(_) => Right(NotificationSuccess(notificationId))
      case _ => Left(NotificationFailure(notificationId, None, None))
    })
}

object FCMExtension extends ExtensionId[FCMExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): FCMExtension = new FCMExtension(system)
}
