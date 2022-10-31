import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"   % "7.8.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"          % "0.73.0",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-28" % "1.0.0",
    "com.github.pathikrit"    %% "better-files"                % "3.9.1",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"          % "0.73.0",
    "org.typelevel"           %% "cats-core"                   % "2.8.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "7.8.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.73.0",
    "org.scalatestplus"       %% "mockito-4-6"                % "3.2.14.0",
    "com.github.tomakehurst"  %  "wiremock-standalone"        % "2.27.2"
  ).map(_ % "test, it")
}
