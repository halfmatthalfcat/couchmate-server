package com.couchmate.external.gracenote.models

import java.time.LocalDateTime

case class GracenoteSlotAiring(
  slot: LocalDateTime,
  channelAiring: GracenoteChannelAiring
)
