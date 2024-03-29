package com.couchmate.services.user.context

case class DeviceContext(
  deviceId: String,
  os: Option[String],
  osVersion: Option[String],
  brand: Option[String],
  model: Option[String]
)
