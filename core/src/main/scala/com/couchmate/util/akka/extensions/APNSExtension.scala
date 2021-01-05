package com.couchmate.util.akka.extensions

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.malliina.push.apns._
import com.typesafe.config.ConfigFactory
import play.api.libs.json.JsValue

import scala.concurrent.Future
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
    token: String,
    body: String,
    title: Option[String] = None,
    data: Map[String, JsValue] = Map()
  ): Future[Either[APNSError, APNSIdentifier]] =
    client.push(
      APNSToken.build(token).get,
      APNSRequest.withTopic(
        topic,
        APNSMessage(
          APSPayload.full(
            AlertPayload(body, title),
            badge = Some(0),
            sound = Some("default")
          )
        ).copy(data = data)
      )
    )

}

object APNSExtension extends ExtensionId[APNSExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): APNSExtension = new APNSExtension(system)
}