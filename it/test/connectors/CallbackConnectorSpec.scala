/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, equalToJson, post, urlMatching}
import com.github.tomakehurst.wiremock.http.Fault
import models.submission.{NotificationRequest, ObjectSummary, SubmissionItem, SubmissionItemStatus}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.AUTHORIZATION
import util.WireMockHelper

import java.time.{Clock, Instant, ZoneOffset}
import java.time.temporal.ChronoUnit

class CallbackConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockHelper {

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private lazy val app: Application = {
    GuiceApplicationBuilder()
      .configure(
        "internal-auth.token" -> "authToken"
      )
      .build()
  }

  private lazy val connector = app.injector.instanceOf[CallbackConnector]

  "notify" - {

    lazy val item = SubmissionItem(
      id = "id",
      owner = "owner",
      callbackUrl = s"http://localhost:${server.port()}/callback",
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = "file",
        contentLength = 1337L,
        contentMd5 = "hash",
        lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
      ),
      failureType = Some(SubmissionItem.FailureType.Sdes),
      failureReason = Some("reason"),
      created = clock.instant(),
      lastUpdated = clock.instant(),
      sdesCorrelationId = "sdesCorrelationId"
    )

    lazy val request = NotificationRequest(
      id = item.id,
      status = item.status,
      objectSummary = item.objectSummary,
      failureType = item.failureType,
      failureReason = item.failureReason,
    )

    "must POST a notification to the callback url in the submission item" in {

      server.stubFor(
        post(urlMatching("/callback"))
          .withHeader(AUTHORIZATION, equalTo("authToken"))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .willReturn(aResponse().withStatus(OK))
      )

      connector.notify(item).futureValue
    }

    "must return a failed future when the callback responds with anything else" in {

      server.stubFor(
        post(urlMatching("/callback"))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .willReturn(aResponse().withBody("body").withStatus(INTERNAL_SERVER_ERROR))
      )

      val exception = connector.notify(item).failed.futureValue
      exception mustEqual CallbackConnector.UnexpectedResponseException(INTERNAL_SERVER_ERROR, "body")
    }

    "must return a failed future when there is a connection error" in {

      server.stubFor(
        post(urlMatching("/callback"))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.notify(item).failed.futureValue
    }
  }
}
