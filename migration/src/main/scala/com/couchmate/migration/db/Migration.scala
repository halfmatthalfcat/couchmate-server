package com.couchmate.migration.db

import java.time.{LocalDateTime, ZoneId}

case class Migration(
  migrationId: Long,
  dateApplied: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
)
