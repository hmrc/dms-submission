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

package api

import com.github.tomakehurst.wiremock.client.WireMock.*
import models.submission.{SubmissionItem, SubmissionItemStatus, SubmissionResponse}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, CREATED, NO_CONTENT, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.{bodyWritableOf_Multipart, readableAsJson, writeableOf_JsValue}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.test.Helpers.AUTHORIZATION
import play.api.test.RunningServer
import repositories.SubmissionItemRepository
import services.{SubmissionReferenceService, UuidService}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.WireMockHelper

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SubmissionSpec extends AnyFreeSpec with Matchers with DefaultPlayMongoRepositorySupport[SubmissionItem] with ScalaFutures
  with IntegrationPatience with WireMockHelper with GuiceOneServerPerSuite with MockitoSugar {

  private val mockSubmissionReferenceService = mock[SubmissionReferenceService]
  private val mockUuidService = mock[UuidService]
  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  private val internalAuthBaseUrl: String = "http://localhost:8470"
  private val sdesStubBaseUrl: String = "http://localhost:9191"
  private val dmsSubmissionAuthToken: String = UUID.randomUUID().toString
  private val clientAuthToken: String = UUID.randomUUID().toString

  override val repository: SubmissionItemRepository =
    app.injector.instanceOf[SubmissionItemRepository]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
      bind[SubmissionReferenceService].toInstance(mockSubmissionReferenceService),
      bind[UuidService].toInstance(mockUuidService),
    )
    .configure(
      "item-timeout" -> "3 seconds",
      "internal-auth.token" -> dmsSubmissionAuthToken,
      "workers.sdes-notification-worker.interval" -> "1 second",
      "workers.sdes-notification-worker.initial-delay" -> "0 seconds",
      "workers.processed-item-worker.interval" -> "1 second",
      "workers.processed-item-worker.initial-delay" -> "0 seconds",
      "workers.failed-item-worker.interval" -> "1 second",
      "workers.failed-item-worker.initial-delay" -> "0 seconds",
      "workers.item-timeout-worker.interval" -> "1 second",
      "workers.item-timeout-worker.initial-delay" -> "0 seconds",
      "create-internal-auth-token-on-start" -> false
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

  private val pdfBytes: ByteString = {
    val stream = getClass.getResourceAsStream("/test.pdf")
    try {
      ByteString(stream.readAllBytes())
    } finally {
      stream.close()
    }
  }

  "Successful submissions must return ACCEPTED and receive callbacks confirming files have been processed" in {

    val submissionReference = "0000-0000-0001"
    val sdesCorrelationId = UUID.randomUUID().toString

    when(mockSubmissionReferenceService.random())
      .thenReturn(submissionReference)

    when(mockUuidService.random())
      .thenReturn(sdesCorrelationId)

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
          DataPart("metadata.customerId", "customerId"),
          DataPart("metadata.submissionMark", "submissionMark"),
          DataPart("metadata.casKey", "casKey"),
          DataPart("metadata.classificationType", "classificationType"),
          DataPart("metadata.businessArea", "businessArea"),
          FilePart(
            key = "form",
            filename = "form.pdf",
            contentType = Some("application/octet-stream"),
            ref = Source.single(pdfBytes),
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

    val submissionReference = "000000000002"
    val sdesCorrelationId = UUID.randomUUID().toString
    val timeOfReceipt = LocalDateTime.now()

    when(mockSubmissionReferenceService.random())
      .thenReturn(submissionReference)

    when(mockUuidService.random())
      .thenReturn(sdesCorrelationId)

    server.stubFor(
      post(urlMatching("/callback"))
        .willReturn(aResponse().withStatus(OK))
    )

    configureFailedCallback(s"$sdesCorrelationId.zip", "unavailable")

    val response = httpClient.url(s"http://localhost:$port/dms-submission/submit")
      .withHttpHeaders(AUTHORIZATION -> clientAuthToken)
      .post(
        Source(Seq(
          DataPart("submissionReference", submissionReference),
          DataPart("callbackUrl", s"http://localhost:${server.port()}/callback"),
          DataPart("metadata.store", "true"),
          DataPart("metadata.source", "api-tests"),
          DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)),
          DataPart("metadata.formId", "formId"),
          DataPart("metadata.customerId", "customerId"),
          DataPart("metadata.submissionMark", "submissionMark"),
          DataPart("metadata.casKey", "casKey"),
          DataPart("metadata.classificationType", "classificationType"),
          DataPart("metadata.businessArea", "businessArea"),
          FilePart(
            key = "form",
            filename = "form.pdf",
            contentType = Some("application/octet-stream"),
            ref = Source.single(pdfBytes),
          ),
          FilePart(
            key = "attachment",
            filename = "extra.pdf",
            contentType = Some("application/pdf"),
            ref = Source.single(ByteString.fromString("Hello, World!"))
          )
        ))
      ).futureValue

    response.status mustEqual ACCEPTED
    val responseBody = response.body[JsValue].as[SubmissionResponse.Success]

    eventually(Timeout(Span(30, Seconds))) {
      server.verify(1, postRequestedFor(urlMatching("/callback"))
        .withRequestBody(matchingJsonPath("$.id", equalTo(responseBody.id)))
        .withRequestBody(matchingJsonPath("$.status", equalTo(SubmissionItemStatus.Failed.toString)))
        .withRequestBody(matchingJsonPath("$.failureType", equalTo("sdes")))
      )
    }
  }

  "Timed out submissions must respond to the callbackUrl with a `Failed` status" in {

    val submissionReference = "000000000002"
    val sdesCorrelationId = UUID.randomUUID().toString
    val timeOfReceipt = LocalDateTime.now()

    when(mockSubmissionReferenceService.random())
      .thenReturn(submissionReference)

    when(mockUuidService.random())
      .thenReturn(sdesCorrelationId)

    server.stubFor(
      post(urlMatching("/callback"))
        .willReturn(aResponse().withStatus(OK))
    )

    configureFailedCallback(s"$sdesCorrelationId.zip", "received")

    val response = httpClient.url(s"http://localhost:$port/dms-submission/submit")
      .withHttpHeaders(AUTHORIZATION -> clientAuthToken)
      .post(
        Source(Seq(
          DataPart("submissionReference", submissionReference),
          DataPart("callbackUrl", s"http://localhost:${server.port()}/callback"),
          DataPart("metadata.store", "true"),
          DataPart("metadata.source", "api-tests"),
          DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)),
          DataPart("metadata.formId", "formId"),
          DataPart("metadata.customerId", "customerId"),
          DataPart("metadata.submissionMark", "submissionMark"),
          DataPart("metadata.casKey", "casKey"),
          DataPart("metadata.classificationType", "classificationType"),
          DataPart("metadata.businessArea", "businessArea"),
          FilePart(
            key = "form",
            filename = "form.pdf",
            contentType = Some("application/octet-stream"),
            ref = Source.single(pdfBytes),
          ),
          FilePart(
            key = "attachment",
            filename = "extra.pdf",
            contentType = Some("application/pdf"),
            ref = Source.single(ByteString.fromString("Hello, World!"))
          )
        ))
      ).futureValue

    response.status mustEqual ACCEPTED
    val responseBody = response.body[JsValue].as[SubmissionResponse.Success]

    eventually(Timeout(Span(30, Seconds))) {
      server.verify(1, postRequestedFor(urlMatching("/callback"))
        .withRequestBody(matchingJsonPath("$.id", equalTo(responseBody.id)))
        .withRequestBody(matchingJsonPath("$.status", equalTo(SubmissionItemStatus.Failed.toString)))
        .withRequestBody(matchingJsonPath("$.failureType", equalTo("timeout")))
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
              "actions" -> List("WRITE")
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

  private def configureFailedCallback(filename: String, failure: String): Unit = {
    val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
    val response = httpClient.url(s"$sdesStubBaseUrl/sdes-stub/configure/notification/fileready?file=$encodedFilename&callback=$failure")
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
