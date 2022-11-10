/*
 * Copyright 2022 HM Revenue & Customs
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

package api

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock._
import models.submission.{SubmissionItemStatus, SubmissionResponse}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, CREATED, NO_CONTENT, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.test.Helpers.AUTHORIZATION
import play.api.test.RunningServer
import util.WireMockHelper

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SubmissionSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockHelper with GuiceOneServerPerSuite {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  private val internalAuthBaseUrl: String = "http://localhost:8470"
  private val sdesStubBaseUrl: String = "http://localhost:9191"
  private val dmsSubmissionAuthToken: String = UUID.randomUUID().toString
  private val clientAuthToken: String = UUID.randomUUID().toString

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "internal-auth.token" -> dmsSubmissionAuthToken,
      "workers.initial-delay" -> "0 seconds",
      "workers.sdes-notification-worker.interval" -> "1 second",
      "workers.processed-item-worker.interval" -> "1 second",
      "workers.failed-item-worker.interval" -> "1 second"
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    if (!authTokenIsValid(dmsSubmissionAuthToken)) createDmsSubmissionAuthToken()
    if (!authTokenIsValid(clientAuthToken)) createClientAuthToken()
    clearSdesCallbacks()
  }

  override protected implicit lazy val runningServer: RunningServer =
    FixedPortTestServerFactory.start(app)

  "Successful submissions must return ACCEPTED and receive callbacks confirming files have been processed" in {

    server.stubFor(
      post(urlMatching("/callback"))
        .willReturn(aResponse().withStatus(OK))
    )

    val timeOfReceipt = LocalDateTime.now()

    val response = httpClient.url(s"http://localhost:$port/dms-submission/submit")
      .withHttpHeaders(AUTHORIZATION -> clientAuthToken)
      .post(
        Source(Seq(
          DataPart("callbackUrl", s"http://localhost:${server.port()}/callback"),
          DataPart("metadata.store", "true"),
          DataPart("metadata.source", "api-tests"),
          DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)),
          DataPart("metadata.formId", "formId"),
          DataPart("metadata.numberOfPages", "1"),
          DataPart("metadata.customerId", "customerId"),
          DataPart("metadata.submissionMark", "submissionMark"),
          DataPart("metadata.casKey", "casKey"),
          DataPart("metadata.classificationType", "classificationType"),
          DataPart("metadata.businessArea", "businessArea"),
          FilePart(
            key = "form",
            filename = "form.pdf",
            contentType = Some("application/octet-stream"),
            ref = Source.single(ByteString("Hello, World!")),
            fileSize = 0
          )
        ))
      ).futureValue

    response.status mustEqual ACCEPTED
    val responseBody = response.body[JsValue].as[SubmissionResponse.Success]

    eventually(Timeout(Span(30, Seconds))) {
      server.verify(1, postRequestedFor(urlMatching("/callback"))
        .withRequestBody(matchingJsonPath("$.id", equalTo(responseBody.id)))
        .withRequestBody(matchingJsonPath("$.status", equalTo(SubmissionItemStatus.Processed.toString)))
      )
    }
  }

  "Failed submissions must respond to the callbackUrl with a `Failed` status" in {

    server.stubFor(
      post(urlMatching("/callback"))
        .willReturn(aResponse().withStatus(OK))
    )

    val id = UUID.randomUUID().toString
    val timeOfReceipt = LocalDateTime.now()

    configureFailedCallback(s"dms-submission/test/$id")

    val response = httpClient.url(s"http://localhost:$port/dms-submission/submit")
      .withHttpHeaders(AUTHORIZATION -> clientAuthToken)
      .post(
        Source(Seq(
          DataPart("correlationId", id),
          DataPart("callbackUrl", s"http://localhost:${server.port()}/callback"),
          DataPart("metadata.store", "true"),
          DataPart("metadata.source", "api-tests"),
          DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)),
          DataPart("metadata.formId", "formId"),
          DataPart("metadata.numberOfPages", "1"),
          DataPart("metadata.customerId", "customerId"),
          DataPart("metadata.submissionMark", "submissionMark"),
          DataPart("metadata.casKey", "casKey"),
          DataPart("metadata.classificationType", "classificationType"),
          DataPart("metadata.businessArea", "businessArea"),
          FilePart(
            key = "form",
            filename = "form.pdf",
            contentType = Some("application/octet-stream"),
            ref = Source.single(ByteString("Hello, World!")),
            fileSize = 0
          )
        ))
      ).futureValue

    response.status mustEqual ACCEPTED
    val responseBody = response.body[JsValue].as[SubmissionResponse.Success]

    eventually(Timeout(Span(30, Seconds))) {
      server.verify(1, postRequestedFor(urlMatching("/callback"))
        .withRequestBody(matchingJsonPath("$.id", equalTo(responseBody.id)))
        .withRequestBody(matchingJsonPath("$.status", equalTo(SubmissionItemStatus.Failed.toString)))
      )
    }
  }

  private def createDmsSubmissionAuthToken(): Unit = {
    val response = httpClient.url(s"$internalAuthBaseUrl/test-only/token")
      .post(
        Json.obj(
          "token" -> dmsSubmissionAuthToken,
          "principal" -> "dms-submission",
          "permissions" -> Seq(
            Json.obj(
              "resourceType" -> "object-store",
              "resourceLocation" -> "dms-submission",
              "actions" -> List("READ", "WRITE", "DELETE")
            )
          )
        )
      ).futureValue
    response.status mustEqual CREATED
  }

  private def createClientAuthToken(): Unit = {
    val response = httpClient.url(s"$internalAuthBaseUrl/test-only/token")
      .post(
        Json.obj(
          "token" -> clientAuthToken,
          "principal" -> "test",
          "permissions" -> Seq(
            Json.obj(
              "resourceType" -> "dms-submission",
              "resourceLocation" -> "submit",
              "actions" -> List("POST")
            )
          )
        )
      ).futureValue
    response.status mustEqual CREATED
  }

  private def authTokenIsValid(token: String): Boolean = {
    val response = httpClient.url(s"$internalAuthBaseUrl/test-only/token")
      .withHttpHeaders("Authorization" -> token)
      .get()
      .futureValue
    response.status == OK
  }

  private def configureFailedCallback(filename: String): Unit = {
    val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
    val response = httpClient.url(s"$sdesStubBaseUrl/sdes-stub/configure/notification/fileready?file=$encodedFilename&callback=unavailable")
      .post(Json.obj())
      .futureValue
    response.status mustEqual NO_CONTENT
  }

  private def clearSdesCallbacks(): Unit = {
    val response = httpClient.url(s"$sdesStubBaseUrl/sdes-stub/configure/notification/fileready")
      .delete()
      .futureValue
    response.status mustEqual OK
  }
}
