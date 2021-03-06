name := "binders-json"

version := "0.12"

organization := "eu.inn"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.11.8", "2.10.6")

resolvers ++= Seq(
    Resolver.sonatypeRepo("public")
  )

libraryDependencies ++= Seq(
  "eu.inn" %% "binders-core" % "0.12.93",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.7.3",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)

libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      libraryDependencies.value
    // in Scala 2.10, quasiquotes are provided by macro paradise
    case Some((2, 10)) =>
      libraryDependencies.value ++ Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
        "org.scalamacros" %% "quasiquotes" % "2.1.0" cross CrossVersion.binary)
  }
}
