package com.couchmate.common.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateUtils {

  def roundNearestHour(dateTime: LocalDateTime): LocalDateTime =
    roundNearestMinute(dateTime).withMinute(0)

  def roundNearestMinute(dateTime: LocalDateTime): LocalDateTime =
    dateTime.withSecond(0).withNano(0)

  def toLocalDateTime(formats: DateTimeFormatter*)(value: String): LocalDateTime = {
    val dateTime: Option[LocalDateTime] = formats.foldLeft[Option[LocalDateTime]](None) { (resolved: Option[LocalDateTime], format: DateTimeFormatter) =>
      if (resolved.isDefined) {
        resolved
      } else {
        try {
          Some(LocalDateTime.parse(value, format))
        } catch {
          case _: Throwable => resolved
        }
      }
    }

    if (dateTime.isDefined) {
      dateTime.get
    } else {
      throw new RuntimeException(s"Unable to derive dateTime $value")
    }
  }

}
