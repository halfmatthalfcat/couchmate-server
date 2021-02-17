package com.couchmate.util.akka.extensions

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.couchmate.services.notification.NotificationCoordinator
import com.couchmate.services.room.{LinkScanner, TenorService}
import com.couchmate.services.{GracenoteCoordinator, GridCoordinator, ListingCoordinator, ListingUpdater, ProviderCoordinator}

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

  val listingUpdater: ActorRef[ListingUpdater.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          ListingUpdater(),
        ).onFailure[Exception](SupervisorStrategy.restart),
        "ListingUpdater"
      )
    )

  val gridCoordinator: ActorRef[GridCoordinator.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          GridCoordinator()
        ).onFailure[Exception](SupervisorStrategy.restart),
        "GridCoordinator"
      )
    )

  val gracenoteCoordinator: ActorRef[GracenoteCoordinator.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          GracenoteCoordinator()
        ).onFailure[Exception](SupervisorStrategy.restart),
        "GracenoteCoordinator"
      )
    )

  val linkScanner: ActorRef[LinkScanner.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          LinkScanner()
        ).onFailure[Exception](SupervisorStrategy.restart),
        "LinkScanner"
      )
    )

  val tenorService: ActorRef[TenorService.Command] =
    singletonManager.init(
      SingletonActor(
        Behaviors.supervise(
          TenorService()
        ).onFailure[Exception](SupervisorStrategy.restart),
        "TenorService"
      )
    )

//  val notificationCoordinator: ActorRef[NotificationCoordinator.Command] =
//    singletonManager.init(
//      SingletonActor(
//        Behaviors.supervise(
//          NotificationCoordinator()
//        ).onFailure[Exception](SupervisorStrategy.restart),
//        "NotificationCoordinator"
//      )
//    )
}

object SingletonExtension extends ExtensionId[SingletonExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): SingletonExtension = new SingletonExtension(system)
}