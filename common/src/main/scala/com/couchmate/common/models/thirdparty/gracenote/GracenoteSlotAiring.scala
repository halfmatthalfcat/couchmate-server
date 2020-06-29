package com.couchmate.common.models.thirdparty.gracenote

import java.time.LocalDateTime

case class GracenoteSlotAiring(
  slot: LocalDateTime,
  channelAiring: GracenoteChannelAiring
)
