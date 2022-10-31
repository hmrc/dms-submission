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

package controllers

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SubmissionService
import better.files.File
import models.submission.SubmissionRequest
import org.mockito.{ArgumentCaptor, Mockito}
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}

import scala.concurrent.Future

class SubmissionControllerSpec extends AnyFreeSpec with Matchers with ScalaFutures with OptionValues with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSubmissionService)
  }

  private val mockSubmissionService = mock[SubmissionService]

  private val app = new GuiceApplicationBuilder()
    .overrides(
      bind[SubmissionService].toInstance(mockSubmissionService)
    )
    .build()

  "submit" - {


    "must return ACCEPTED when a submission is successful" in {

      val fileCaptor: ArgumentCaptor[File] = ArgumentCaptor.forClass(classOf[File])

      when(mockSubmissionService.submit(any(), any())(any()))
        .thenReturn(Future.successful("correlationId"))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeText("Hello, World!")

      val request = FakeRequest(routes.SubmissionController.submit)
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("callbackUrl")
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

      val expectedRequest = SubmissionRequest("callbackUrl")

      val result = route(app, request).value

      status(result) mustEqual ACCEPTED
      contentAsJson(result) mustEqual Json.obj("correlationId" -> "correlationId")

      verify(mockSubmissionService, times(1)).submit(eqTo(expectedRequest), fileCaptor.capture())(any())

      fileCaptor.getValue.contentAsString mustEqual betterTempFile.contentAsString
    }

    "must fail when the submission fails" in {

      when(mockSubmissionService.submit(any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val tempFile = SingletonTemporaryFileCreator.create()
      val betterTempFile = File(tempFile.toPath)
        .deleteOnExit()
        .writeText("Hello, World!")

      val request = FakeRequest(routes.SubmissionController.submit)
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "callbackUrl" -> Seq("callbackUrl")
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

    "must return BAD_REQUEST when the user provides an invalid request" in {

      val request = FakeRequest(routes.SubmissionController.submit)
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map.empty,
            files = Seq.empty,
            badParts = Seq.empty
          )
        )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.obj(
        "errors" -> Seq(
          "callbackUrl is required",
          "file is required"
        )
      )

      verify(mockSubmissionService, never()).submit(any(), any())(any())
    }
  }
}
