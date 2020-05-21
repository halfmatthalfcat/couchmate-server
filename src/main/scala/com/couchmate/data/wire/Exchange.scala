package com.couchmate.data.wire

/**
 * Streamlines AMQP Exchange Identification
 */

import enumeratum.EnumEntry.Lowercase
import enumeratum._

sealed trait Exchange extends EnumEntry with Lowercase

object Exchange extends Enum[Exchange] {
  val values = findValues

  case object Messages extends Exchange
}
