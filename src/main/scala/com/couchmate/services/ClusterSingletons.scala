package com.couchmate.services

import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.couchmate.services.thirdparty.gracenote.GracenoteServices
import com.couchmate.services.thirdparty.gracenote.listing.ListingCoordinator
import com.couchmate.services.thirdparty.gracenote.provider.ProviderCoordinator

trait ClusterSingletons extends GracenoteServices {
  val system: ActorSystem[Nothing]

  private[this] val singletonManager: ClusterSingleton =
    ClusterSingleton(system)

  lazy val listingCoordinator: ActorRef[ListingCoordinator.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          ListingCoordinator(listingIngestor),
        ).onFailure[Exception](SupervisorStrategy.restart),
        "ListingCoordinator",
      ),
    )

  lazy val providerCoordinator: ActorRef[ProviderCoordinator.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          ProviderCoordinator(providerIngestor),
        ).onFailure[Exception](SupervisorStrategy.restart),
        "ProviderCoordinator",
      ),
    )
}
