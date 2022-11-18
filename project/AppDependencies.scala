import sbt._

object AppDependencies {

  private val hmrcMongoVersion = "0.73.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"    % "7.8.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"           % hmrcMongoVersion,
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-28"  % "1.0.0",
    "com.github.pathikrit"    %% "better-files"                 % "3.9.1",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"           % "0.73.0",
    "org.typelevel"           %% "cats-core"                    % "2.8.0",
    "org.typelevel"           %% "cats-effect"                  % "3.4.0",
    "co.fs2"                  %% "fs2-core"                     % "3.3.0",
    "org.scala-lang.modules"  %% "scala-xml"                    % "1.3.0",
    "uk.gov.hmrc"             %% "internal-auth-client-play-28" % "1.2.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-metrix-play-28"    % hmrcMongoVersion
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"        % "7.8.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"       % hmrcMongoVersion,
    "org.scalatestplus"       %% "mockito-4-6"                   % "3.2.14.0",
    "org.typelevel"           %% "cats-effect-testkit"           % "3.4.0",
    "org.typelevel"           %% "cats-effect-testing-scalatest" % "1.5.0",
    "com.github.tomakehurst"  %  "wiremock-standalone"           % "2.27.2",
    "com.vladsch.flexmark"    %  "flexmark-all"                  % "0.62.2"
  ).map(_ % "test, it")
}
