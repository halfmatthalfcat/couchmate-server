package com.couchmate.common.models.data

import enumeratum._

sealed trait ApplicationPlatform extends EnumEntry

object ApplicationPlatform
  extends Enum[ApplicationPlatform]
  with PlayJsonEnum[ApplicationPlatform] {
  val values = findValues

  case object iOS     extends ApplicationPlatform
  case object iPadOS  extends ApplicationPlatform
  case object Android extends ApplicationPlatform
  case object Web     extends ApplicationPlatform
  case object Windows extends ApplicationPlatform
  case object Unknown extends ApplicationPlatform
}
