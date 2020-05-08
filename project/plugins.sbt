logLevel := Level.Debug

resolvers += Resolver.sonatypeRepo("snapshot")

// for autoplugins
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.2")

addSbtPlugin("com.github.scala2ts" % "scala2ts-sbt" % "1.0.5-SNAPSHOT")
