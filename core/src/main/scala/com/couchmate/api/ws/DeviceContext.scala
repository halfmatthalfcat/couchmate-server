package com.couchmate.api.ws

case class DeviceContext(
  os: Option[String],
  osVersion: Option[String],
  brand: Option[String],
  model: Option[String]
)
