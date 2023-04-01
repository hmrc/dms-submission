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

package controllers

import better.files.File
import cats.data.NonEmptyChain
import models.Pdf
import models.submission.{Attachment, SubmissionMetadata, SubmissionRequest, SubmissionResponse}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import services.SubmissionService
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionControllerSpec extends AnyFreeSpec with Matchers with ScalaFutures with OptionValues with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](mockSubmissionService, mockStubBehaviour)
  }

  private val mockSubmissionService = mock[SubmissionService]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)
  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("dms-submission"),
      resourceLocation = ResourceLocation("submit")
    ),
    action = IAAction("WRITE")
  )

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[SubmissionService].toInstance(mockSubmissionService),
      bind[BackendAuthComponents].toInstance(backendAuthComponents)
    )
    .build()

  private val pdfBytes: Array[Byte] = {
    val stream = getClass.getResourceAsStream("/test.pdf")
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }

  "submit" - {

    "must return ACCEPTED when a submission is successful" in {

      val fileCaptor: ArgumentCaptor[Pdf] = ArgumentCaptor.forClass(classOf[Pdf])

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.username))
        .thenReturn(Future.successful(Retrieval.Username("test-service")))

      when(mockSubmissionService.submit(any(), any(), any())(any()))
        .thenReturn(Future.successful(Right("submissionReference")))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeByteArray(pdfBytes)

      val request = FakeRequest(routes.SubmissionController.submit)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("http://localhost/callback"),
              "metadata.source" -> Seq("source"),
              "metadata.timeOfReceipt" -> Seq(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.of(2022, 2, 1, 0, 0, 0))),
              "metadata.formId" -> Seq("formId"),
              "metadata.customerId" -> Seq("customerId"),
              "metadata.classificationType" -> Seq("classificationType"),
              "metadata.businessArea" -> Seq("businessArea"),
              "attachments[0].location" -> Seq("file.pdf"),
              "attachments[0].contentMd5" -> Seq("lpSKrT/K6AwIo1ybWVjNiQ==")
            ),
            files = Seq(
              MultipartFormData.FilePart(
                key = "form",
                filename = "form.pdf",
                contentType = Some("application/pdf"),
                ref = tempFile,
                fileSize = betterTempFile.size
              )
            ),
            badParts = Seq.empty
          )
        )

      val expectedMetadata = SubmissionMetadata(
        store = true,
        source = "source",
        timeOfReceipt = LocalDateTime.of(2022, 2, 1, 0, 0, 0).toInstant(ZoneOffset.UTC),
        formId = "formId",
        customerId = "customerId",
        submissionMark = None,
        casKey = None,
        classificationType = "classificationType",
        businessArea = "businessArea"
      )

      val expectedRequest = SubmissionRequest(
        submissionReference = None,
        callbackUrl = "http://localhost/callback",
        metadata = expectedMetadata,
        attachments = Seq(
          Attachment(
            location = "file.pdf",
            contentMd5 = "lpSKrT/K6AwIo1ybWVjNiQ==",
            owner = "test-service"
          )
        )
      )

      val result = route(app, request).value

      status(result) mustEqual ACCEPTED
      contentAsJson(result) mustEqual Json.obj("id" -> "submissionReference")

      verify(mockSubmissionService, times(1)).submit(eqTo(expectedRequest), fileCaptor.capture(), eqTo("test-service"))(any())

      // TODO make this less flaky
      fileCaptor.getValue.file.contentAsString mustEqual betterTempFile.contentAsString
    }

    "must fail when the submission fails" in {

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.username))
        .thenReturn(Future.successful(Retrieval.Username("test-service")))

      when(mockSubmissionService.submit(any(), any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeByteArray(pdfBytes)

      val request = FakeRequest(routes.SubmissionController.submit)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("http://localhost/callback"),
              "metadata.source" -> Seq("source"),
              "metadata.timeOfReceipt" -> Seq(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.of(2022, 2, 1, 0, 0, 0))),
              "metadata.formId" -> Seq("formId"),
              "metadata.customerId" -> Seq("customerId"),
              "metadata.classificationType" -> Seq("classificationType"),
              "metadata.businessArea" -> Seq("businessArea")
            ),
            files = Seq(
              MultipartFormData.FilePart(
                key = "form",
                filename = "form.pdf",
                contentType = Some("application/pdf"),
                ref = tempFile,
                fileSize = betterTempFile.size
              )
            ),
            badParts = Seq.empty
          )
        )

      route(app, request).value.failed.futureValue
    }

    "must return BAD_REQUEST when the submission service returns errors" in {

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.username))
        .thenReturn(Future.successful(Retrieval.Username("test-service")))

      when(mockSubmissionService.submit(any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(NonEmptyChain.one("some error"))))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeByteArray(pdfBytes)

      val request = FakeRequest(routes.SubmissionController.submit)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("http://localhost/callback"),
              "metadata.source" -> Seq("source"),
              "metadata.timeOfReceipt" -> Seq(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.of(2022, 2, 1, 0, 0, 0))),
              "metadata.formId" -> Seq("formId"),
              "metadata.customerId" -> Seq("customerId"),
              "metadata.classificationType" -> Seq("classificationType"),
              "metadata.businessArea" -> Seq("businessArea")
            ),
            files = Seq(
              MultipartFormData.FilePart(
                key = "form",
                filename = "form.pdf",
                contentType = Some("application/pdf"),
                ref = tempFile,
                fileSize = betterTempFile.size
              )
            ),
            badParts = Seq.empty
          )
        )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      val responseBody = contentAsJson(result).as[SubmissionResponse.Failure]
      responseBody.errors must contain only "some error"
    }

    "must return BAD_REQUEST when the user provides an invalid request" in {

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.username))
        .thenReturn(Future.successful(Retrieval.Username("test-service")))

      val request = FakeRequest(routes.SubmissionController.submit)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map.empty,
            files = Seq.empty,
            badParts = Seq.empty
          )
        )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      val responseBody = contentAsJson(result).as[SubmissionResponse.Failure]
      responseBody.errors must contain allOf(
        "callbackUrl: This field is required",
        "form: This field is required"
      )

      verify(mockSubmissionService, never()).submit(any(), any(), any())(any())
    }

    "must return BAD_REQUEST when the user provides a file which is not a pdf" in {

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.username))
        .thenReturn(Future.successful(Retrieval.Username("test-service")))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeText("Hello, World!")

      val request = FakeRequest(routes.SubmissionController.submit)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("http://localhost/callback"),
              "metadata.source" -> Seq("source"),
              "metadata.timeOfReceipt" -> Seq(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.of(2022, 2, 1, 0, 0, 0))),
              "metadata.formId" -> Seq("formId"),
              "metadata.customerId" -> Seq("customerId"),
              "metadata.classificationType" -> Seq("classificationType"),
              "metadata.businessArea" -> Seq("businessArea")
            ),
            files = Seq(
              MultipartFormData.FilePart(
                key = "form",
                filename = "form.pdf",
                contentType = Some("application/pdf"),
                ref = tempFile,
                fileSize = betterTempFile.size
              )
            ),
            badParts = Seq.empty
          )
        )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      val responseBody = contentAsJson(result).as[SubmissionResponse.Failure]
      responseBody.errors must contain ("form: error.pdf.invalid")

      verify(mockSubmissionService, never()).submit(any(), any(), any())(any())
    }

    "must fail when the user is not authorised" in {

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
        .thenReturn(Future.failed(new RuntimeException()))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeText("Hello, World!")

      val request = FakeRequest(routes.SubmissionController.submit)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("http://localhost/callback"),
              "metadata.source" -> Seq("source"),
              "metadata.timeOfReceipt" -> Seq(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.of(2022, 2, 1, 0, 0, 0))),
              "metadata.formId" -> Seq("formId"),
              "metadata.customerId" -> Seq("customerId"),
              "metadata.classificationType" -> Seq("classificationType"),
              "metadata.businessArea" -> Seq("businessArea")
            ),
            files = Seq(
              MultipartFormData.FilePart(
                key = "form",
                filename = "form.pdf",
                contentType = Some("application/pdf"),
                ref = tempFile,
                fileSize = betterTempFile.size
              )
            ),
            badParts = Seq.empty
          )
        )

      route(app, request).value.failed.futureValue

      verify(mockSubmissionService, never()).submit(any(), any(), any())(any())
    }
  }
}
