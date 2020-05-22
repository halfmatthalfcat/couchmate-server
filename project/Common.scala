/**
 * Common SBT Utilities
 */

import sbt._

object Common {

  def akka(module: String): ModuleID =
    "com.typesafe.akka" %% s"akka-$module" % Versions.akka

  def alpakka(module: String): ModuleID =
    "com.lightbend.akka" %% s"akka-stream-alpakka-$module" % Versions.alpakka

  def slick(module: String): ModuleID =
    "com.typesafe.slick" %% module % Versions.slick

  def slickPg(module: String = "slick-pg"): ModuleID = {
    val trueModule: String = module match {
      case "slick-pg" => "slick-pg"
      case _ => s"slick-pg_$module"
    }

    "com.github.tminglei" %% trueModule % Versions.slickPg
  }
}
