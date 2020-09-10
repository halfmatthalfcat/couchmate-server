package com.couchmate.api.ws.util

import enumeratum._

sealed trait ConnectionStatus extends EnumEntry

object ConnectionStatus extends Enum[ConnectionStatus] {
  val values = findValues

  case object Good      extends ConnectionStatus
  case object Degraded  extends ConnectionStatus
  case object Weak      extends ConnectionStatus
  case object Lost      extends ConnectionStatus
  case object Unknown   extends ConnectionStatus
}
