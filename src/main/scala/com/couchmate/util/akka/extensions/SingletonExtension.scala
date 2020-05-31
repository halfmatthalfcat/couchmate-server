package com.couchmate.util.akka.extensions

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.couchmate.external.gracenote.listing.ListingCoordinator
import com.couchmate.external.gracenote.provider.ProviderCoordinator

class SingletonExtension(system: ActorSystem[_]) extends Extension {
  private[this] val singletonManager: ClusterSingleton = ClusterSingleton(system)

  val listingCoordinator: ActorRef[ListingCoordinator.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          ListingCoordinator(),
        ).onFailure[Exception](SupervisorStrategy.restart),
        "ListingCoordinator",
      ),
    )

  val providerCoordinator: ActorRef[ProviderCoordinator.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          ProviderCoordinator(),
        ).onFailure[Exception](SupervisorStrategy.restart),
        "ProviderCoordinator",
      ),
    )
}

object SingletonExtension extends ExtensionId[SingletonExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): SingletonExtension = new SingletonExtension(system)
}