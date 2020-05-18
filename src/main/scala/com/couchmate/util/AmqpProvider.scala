package com.couchmate.util

import com.typesafe.config.Config
import akka.stream.alpakka.amqp._
import com.typesafe.scalalogging.LazyLogging

trait AmqpProvider extends LazyLogging {
  val config: Config

  private[this] val amqpProvider: AmqpDetailsConnectionProvider =
    AmqpDetailsConnectionProvider(
      config.getString("amqp.host"),
      config.getInt("amqp.port")
    ).withCredentials(
      AmqpCredentials(
        config.getString("amqp.credentials.username"),
        config.getString("amqp.credentials.password"),
      )
    ).withVirtualHost(
      config.getString("amqp.vhost")
    )

  lazy val amqp: AmqpCachedConnectionProvider =
    AmqpCachedConnectionProvider(amqpProvider)
}
