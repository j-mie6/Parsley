val sbtTypelevelVersion = "0.7.5"
val circeVersion = "0.14.10"

resolvers ++= Opts.resolver.sonatypeOssSnapshots
resolvers ++= Opts.resolver.sonatypeOssReleases

scalacOptions ++= Seq("-unchecked", "-feature","-deprecation")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencySchemes ++= Seq(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always,
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
)

addSbtPlugin("org.typelevel" % "sbt-typelevel" % sbtTypelevelVersion)
addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % sbtTypelevelVersion)

// CI Stuff
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.16.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.6")

addSbtPlugin("com.beautiful-scala" % "sbt-scalastyle" % "1.5.1")
addSbtPlugin("org.jmotor.sbt" % "sbt-dependency-updates" % "1.2.9")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.2")
addSbtPlugin("com.timushev.sbt" % "sbt-rewarn" % "0.1.3")
