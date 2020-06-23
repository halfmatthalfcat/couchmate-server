import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.universal.UniversalKeys

/**
 * Custom sbt-release steps
 */

object Release extends UniversalKeys {

  lazy val runCompile: ReleaseStep = ReleaseStep(
    action = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(compile in Compile in ref, st)
    }
  )

  lazy val stageDockerImage: ReleaseStep = ReleaseStep(
    action = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(stage in Docker in ref, st)
    }
  )

  lazy val publishDockerImage: ReleaseStep = ReleaseStep(
    action = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(publish in Docker in ref, st)
    }
  )

}
