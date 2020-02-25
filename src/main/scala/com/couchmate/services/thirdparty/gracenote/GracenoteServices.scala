package com.couchmate.services.thirdparty.gracenote

import akka.actor.typed.ActorSystem
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.actor.typed.scaladsl.adapter._
import com.couchmate.data.db.CMDatabase
import com.couchmate.services.thirdparty.gracenote.listing.ListingIngestor
import com.couchmate.services.thirdparty.gracenote.provider.ProviderIngestor
import com.typesafe.config.Config

trait GracenoteServices {
  val system: ActorSystem[Nothing]
  val config: Config
  val db: CMDatabase

  private[GracenoteServices] implicit val actorSystem: ClassicActorSystem =
    system.toClassic

  private[this] val gracenoteService: GracenoteService =
    new GracenoteService(config);

  val providerIngestor: ProviderIngestor =
    new ProviderIngestor(
      gracenoteService,
      db,
    )

  val listingIngestor: ListingIngestor =
    new ListingIngestor(
      gracenoteService,
      providerIngestor,
      db,
    )
}
