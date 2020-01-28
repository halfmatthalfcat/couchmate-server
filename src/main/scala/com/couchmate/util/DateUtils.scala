package com.couchmate.util

import java.time.OffsetDateTime

object DateUtils {

  def roundNearestHour(dateTime: OffsetDateTime): OffsetDateTime = {
    dateTime
      .withSecond(0)
      .withMinute(0)
      .withNano(0)
  }

}
