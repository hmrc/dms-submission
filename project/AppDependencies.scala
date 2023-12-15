import sbt._

object AppDependencies {

  private val hmrcMongoVersion = "1.6.0"
  private val bootstrapVersion = "8.2.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"           % hmrcMongoVersion,
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-30"  % "1.3.0",
    "com.github.pathikrit"    %% "better-files"                 % "3.9.1",
    "org.typelevel"           %% "cats-core"                    % "2.8.0",
    "org.typelevel"           %% "cats-effect"                  % "3.4.0",
    "co.fs2"                  %% "fs2-core"                     % "3.3.0",
    "uk.gov.hmrc"             %% "internal-auth-client-play-30" % "1.9.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-metrix-play-30"    % hmrcMongoVersion,
    "org.apache.pdfbox"       %  "pdfbox"                       % "2.0.27"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"        % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"       % hmrcMongoVersion,
    "org.typelevel"           %% "cats-effect-testkit"           % "3.4.0",
    "org.typelevel"           %% "cats-effect-testing-scalatest" % "1.5.0"
  ).map(_ % "test, it")
}
