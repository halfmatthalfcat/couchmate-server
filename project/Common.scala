/**
 * Common SBT Utilities
 */

import sbt._

object Common {

  def akka(module: String): ModuleID =
    "com.typesafe.akka" %% s"akka-$module" % Versions.akka

  def akkaManagement(module: String = ""): ModuleID = {
    val mod: String = module match {
      case "" => "akka-management"
      case _ => s"akka-management-$module"
    }

    "com.lightbend.akka.management" %% mod % Versions.akkaManagement
  }

  def alpakka(module: String): ModuleID =
    "com.lightbend.akka" %% s"akka-stream-alpakka-$module" % Versions.alpakka

  def slick(module: String): ModuleID =
    "com.typesafe.slick" %% module % Versions.slick

  def slickPg(module: String = ""): ModuleID = {
    val trueModule: String = module match {
      case "" => "slick-pg"
      case _ => s"slick-pg_$module"
    }

    "com.github.tminglei" %% trueModule % Versions.slickPg
  }

  def scalaCache(module: String = ""): ModuleID = {
    val trueModule: String = module match {
      case "" => "scalacache-core"
      case _ => s"scalacache-$module"
    }

    "com.github.cb372" %% trueModule % Versions.scalaCache
  }

  def kamon(module: String): ModuleID =
    s"io.kamon" %% s"kamon-$module" % Versions.kamon
}
