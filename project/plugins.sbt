logLevel := Level.Debug

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.2")

addSbtPlugin("com.github.scala2ts" % "scala2ts-sbt" % "1.1.4")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.2")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.6")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.27")
