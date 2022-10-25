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

import models.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.MultipartFormData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SubmissionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SubmissionControllerSpec extends AnyFreeSpec with Matchers with ScalaFutures with OptionValues with MockitoSugar {

  "submit" - {

    val mockSubmissionService = mock[SubmissionService]

    val app = new GuiceApplicationBuilder()
      .overrides(
        bind[SubmissionService].toInstance(mockSubmissionService)
      )
      .build()

    "must return accepted when a submission is successful" in {

      when(mockSubmissionService.submit(any(), any())(any()))
        .thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.SubmissionController.submit)
        .withMultipartFormDataBody(MultipartFormData(Map.empty, Seq.empty, Seq.empty))
      implicit val hc: HeaderCarrier = HeaderCarrier()

      val result = route(app, request).value

      status(result) mustEqual ACCEPTED
    }

    "must fail when the submission fails" in {

      when(mockSubmissionService.submit(any(), any())(any()))
        .thenReturn(Future.failed(new Exception()))

      val request = FakeRequest(routes.SubmissionController.submit)
        .withMultipartFormDataBody(MultipartFormData(Map.empty, Seq.empty, Seq.empty))
      implicit val hc: HeaderCarrier = HeaderCarrier()

      route(app, request).value.failed.futureValue
    }
  }
}
