package com.couchmate.data.db

import com.couchmate.common.models.Channel

class ChannelDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val channelInsertMeta =
    insertMeta[Channel](_.channelId)

  def getChannel(channelId: Long) = quote {
    query[Channel]
      .filter(_.channelId.contains(lift(channelId)))
  }

  def getChannelForExt(extId: Long) = quote {
    query[Channel]
      .filter(_.extId == lift(extId))
  }

  def upsertChannel(channel: Channel) = quote {
    query[Channel]
      .insert(lift(channel))
      .onConflictUpdate(_.channelId)(
        (from, to) => from.extId -> to.extId,
        (from, to) => from.callsign -> to.callsign
      ).returning(c => c)
  }

}
