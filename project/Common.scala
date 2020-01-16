/**
 * Common SBT Utilities
 */

import sbt._

object Common {

  def akka(module: String): ModuleID = "com.typesafe.akka" %% s"akka-$module" % Versions.akka
  def slick(module: String): ModuleID = "com.typesafe.slick" %% module % Versions.slick

}
