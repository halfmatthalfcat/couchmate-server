package com.couchmate.common.models.data

import enumeratum._

sealed trait UserNotificationQueueItemType extends EnumEntry

object UserNotificationQueueItemType
  extends Enum[UserNotificationQueueItemType]
  with PlayJsonEnum[UserNotificationQueueItemType] {
  val values = findValues

  case object Show    extends UserNotificationQueueItemType
  case object Episode extends UserNotificationQueueItemType
  case object Team    extends UserNotificationQueueItemType
}
