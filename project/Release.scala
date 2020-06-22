import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep

/**
 * Custom sbt-release steps
 */

object Release {

  lazy val runCompile: ReleaseStep = ReleaseStep(
    action = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(compile in Compile in ref, st)
    }
  )

}
