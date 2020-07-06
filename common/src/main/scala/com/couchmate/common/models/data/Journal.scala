package com.couchmate.common.models.data

case class Journal(
  ordering: Option[Long],
  persistenceId: String,
  sequenceNumber: Long,
  deleted: Boolean,
  tags: Option[String],
  message: Array[Byte]
)
