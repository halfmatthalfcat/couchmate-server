package com.couchmate.data.db

import com.couchmate.common.models.ProviderChannel

class ProviderChannelDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val providerChannelInsertMeta =
    insertMeta[ProviderChannel](_.providerChannelId)

  def getProviderChannel(providerChannelId: Long) = ctx.run(quote {
    query[ProviderChannel]
      .filter(_.providerChannelId.contains(providerChannelId))
  }).headOption

  def getProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long,
  ) = ctx.run(quote {
    query[ProviderChannel]
      .filter { pc =>
        pc.providerId == providerId &&
        pc.channelId == channelId
      }
  }).headOption

  def upsertProviderChannel(providerChannel: ProviderChannel) = ctx.run(quote {
    query[ProviderChannel]
      .insert(lift(providerChannel))
      .onConflictUpdate(_.providerChannelId)(
        (from, to) => from.providerId -> to.providerId,
        (from, to) => from.channelId -> to.channelId,
        (from, to) => from.channel -> to.channel
      ).returning(pc => pc)
  })

}
