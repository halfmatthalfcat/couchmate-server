package com.couchmate.data.db

import com.couchmate.common.models.ProviderChannel

class ProviderChannelDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val providerChannelInsertMeta =
    insertMeta[ProviderChannel](_.providerChannelId)

  def getProviderChannel(providerChannelId: Long) = quote {
    query[ProviderChannel]
      .filter(_.providerChannelId.contains(providerChannelId))
  }

  def getProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long,
  ) = quote {
    query[ProviderChannel]
      .filter { pc =>
        pc.providerId == providerId &&
        pc.channelId == channelId
      }
  }

  def upsertProviderChannel(providerChannel: ProviderChannel) = quote {
    query[ProviderChannel]
      .insert(lift(providerChannel))
      .onConflictUpdate(_.providerChannelId)(
        (from, to) => from.providerId -> to.providerId,
        (from, to) => from.channelId -> to.channelId,
        (from, to) => from.channel -> to.channel
      ).returning(pc => pc)
  }

}
