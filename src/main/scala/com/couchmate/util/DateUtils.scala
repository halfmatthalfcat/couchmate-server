package com.couchmate.util

import java.time.LocalDateTime

object DateUtils {

  def roundNearestHour(dateTime: LocalDateTime): LocalDateTime = {
    dateTime
      .withSecond(0)
      .withMinute(0)
      .withNano(0)
  }

}
