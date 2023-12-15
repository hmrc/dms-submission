import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.targetJvm

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / targetJvm := "jvm-11"

lazy val microservice = Project("dms-submission", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, BuildInfoPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += s"-Wconf:src=routes/.*:s,src=src_managed/.*:s",
    PlayKeys.playDefaultPort := 8222,
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, PlayKeys.playDefaultPort),
    buildInfoPackage := "buildinfo",
    RoutesKeys.routesImport ++= Seq(
      "models._",
      "models.submission._",
      "java.time.LocalDate"
    ),
    CodeCoverageSettings.settings,
    resolvers += Resolver.jcenterRepo,
    inConfig(Test)(testSettings),
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(
    DefaultBuildSettings.itSettings,
    libraryDependencies ++= AppDependencies.integration,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources"
  )

lazy val testSettings: Seq[Def.Setting[_]] = Seq(
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources"
)