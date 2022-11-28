import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import play.sbt.routes.RoutesKeys

lazy val microservice = Project("dms-submission", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, BuildInfoPlugin)
  .settings(
    majorVersion        := 0,
    scalaVersion        := "2.13.10",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    PlayKeys.playDefaultPort := 8222,
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, PlayKeys.playDefaultPort),
    buildInfoPackage := "buildinfo",
    RoutesKeys.routesImport ++= Seq(
      "models._",
      "models.submission._",
      "java.time.LocalDate"
    )
  )
  .settings(publishingSettings: _*)
  .settings(inConfig(Test)(testSettings): _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(inConfig(IntegrationTest)(itSettings): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)

lazy val testSettings: Seq[Def.Setting[_]] = Seq(
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources"
)

lazy val itSettings: Seq[Def.Setting[_]] = Seq(
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources"
)