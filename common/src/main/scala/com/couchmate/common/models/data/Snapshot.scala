package com.couchmate.common.models.data

case class Snapshot(
  persistenceId: String,
  sequenceNumber: Long,
  created: Long,
  snapshot: Array[Byte]
)
